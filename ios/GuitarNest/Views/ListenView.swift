import GuitarNestShared
import SwiftUI
import UniformTypeIdentifiers

/// Tab from audio: a file the player owns or a microphone recording. Notes are found on this iPhone;
/// the player's own AI may then refine the draft. iOS does not let apps capture other apps' sound.
struct ListenView: View {
    @EnvironmentObject var model: AppModel
    @Binding var path: [Route]
    @StateObject private var recorder = Recorder()
    @State private var title = ""
    @State private var level = 1
    @State private var refine = AIConnection.load().configured
    @State private var picking = false
    @State private var step: String?
    @State private var progress: Double = 0
    @State private var error: String?
    @AppStorage("identify") private var identify = false

    private let aiReady = AIConnection.load().configured

    var body: some View {
        Form {
            Section {
                Text(L10n.t("listen_help")).font(.callout)
                TextField(L10n.t("title"), text: $title)
                Picker(L10n.t("level"), selection: $level) { ForEach(0..<3, id: \.self) { Text(levelName($0)).tag($0) } }
                    .pickerStyle(.segmented)
                Toggle(L10n.t(aiReady ? "improve_ai" : "improve_ai_off"), isOn: $refine).disabled(!aiReady)
                if Bridge.shared.acoustIdAvailable() {
                    Toggle(isOn: $identify) {
                        VStack(alignment: .leading) {
                            Text(L10n.t("identify"))
                            Text(L10n.t("identify_note")).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
            }
            if let step {
                Section {
                    Text(step).font(.headline)
                    ProgressView(value: progress)
                }
            } else if recorder.active {
                Section {
                    Text(L10n.t("recording")).font(.headline)
                    Text(String(format: "%d:%02d", Int(recorder.seconds) / 60, Int(recorder.seconds) % 60)).font(.system(size: 40, weight: .heavy))
                    ProgressView(value: Double(min(1, recorder.level * 4)))
                    Button(L10n.t("stop_make")) {
                        let audio = recorder.stop()
                        make(audio)
                    }
                    .buttonStyle(.borderedProminent)
                    Button(L10n.t("cancel"), role: .cancel) { _ = recorder.stop() }
                }
            } else {
                if let error { Text(error).foregroundStyle(Palette.off) }
                Section {
                    source("doc.richtext", "source_file", "listen_files_note") { picking = true }
                    source("mic", "source_record", "listen_record_note") {
                        error = nil
                        recorder.start { error = L10n.t("mic_why") }
                    }
                }
            }
            Text(L10n.t("listen_privacy")).font(.caption).foregroundStyle(.secondary)
        }
        .navigationTitle(L10n.t("listen_title"))
        .navigationBarTitleDisplayMode(.inline)
        .fileImporter(isPresented: $picking, allowedContentTypes: [.audio]) { result in
            guard case .success(let url) = result else { return }
            let fileName = url.deletingPathExtension().lastPathComponent
            if title.isEmpty && !lookUp { title = fileName }
            step = L10n.t("step_reading")
            Task.detached {
                do {
                    let audio = try AudioInput.decode(url)
                    await MainActor.run { make(audio, fallback: fileName) }
                } catch {
                    await MainActor.run { step = nil; self.error = error.localizedDescription }
                }
            }
        }
        .onDisappear { if recorder.active { _ = recorder.stop() } }
    }

    private func source(_ icon: String, _ label: String, _ note: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 16) {
                Image(systemName: icon).font(.title2).foregroundStyle(Palette.primary)
                VStack(alignment: .leading) {
                    Text(L10n.t(label)).font(.headline).foregroundStyle(.primary)
                    Text(L10n.t(note)).font(.caption).foregroundStyle(.secondary)
                }
            }
        }
    }

    private var lookUp: Bool { identify && Bridge.shared.acoustIdAvailable() }

    /// Result of an AcoustID lookup: the title and artist, or a problem to show.
    private struct Found {
        var title = ""
        var artist = ""
        var problem: String?
    }

    /// AcoustID lookup when the title is empty.
    private static func findTitle(_ audio: [Float]) async -> Found {
        guard let form = Bridge.shared.acoustIdForm(audio: Convert.floats(audio), sampleRate: Int32(AudioInput.rate)) else {
            return Found(problem: L10n.t("identify_too_short"))
        }
        do {
            guard let best = try await AIClient.identify(form: form).first else { return Found(problem: L10n.t("identify_none")) }
            return Found(title: best.title, artist: best.artist)
        } catch {
            return Found(problem: error.localizedDescription)
        }
    }

    private func make(_ audio: [Float], fallback: String = "") {
        guard audio.count >= Int(AudioInput.rate) * 3 else {
            error = L10n.t("listen_too_short")
            step = nil
            return
        }
        step = L10n.t("step_listening")
        progress = 0
        let levelValue = Bridge.shared.level(index: Int32(level))
        let typed = title.trimmingCharacters(in: .whitespaces)
        let findIt = typed.isEmpty && lookUp
        let useAI = refine && aiReady
        Task.detached(priority: .userInitiated) {
            do {
                var name = typed.isEmpty ? fallback : typed
                var artist = ""
                var problem: String?
                if findIt {
                    await MainActor.run { step = L10n.t("step_identifying") }
                    let found = await ListenView.findTitle(audio)
                    if let reason = found.problem { problem = reason } else { name = found.title; artist = found.artist }
                    await MainActor.run { step = L10n.t("step_listening") }
                }
                guard let url = Bundle.main.url(forResource: "basic-pitch", withExtension: "gnm") else { throw AppError("Model missing") }
                let pitch = try Bridge.shared.basicPitch(model: Convert.bytes(try Data(contentsOf: url)))
                let draft = try Bridge.shared.listen(model: pitch, audio: Convert.floats(audio), level: levelValue, title: name) { done, total in
                    let value = Double(truncating: done) / Double(max(1, Int(truncating: total)))
                    Task { @MainActor in progress = value }
                }
                guard let draft else {
                    await MainActor.run { step = nil; error = L10n.t("listen_no_notes") }
                    return
                }
                var song = draft
                if useAI {
                    await MainActor.run { step = L10n.t("step_asking") }
                    do {
                        song = try await AIClient.makeSong(AIConnection.load(), taskIndex: 3, level: levelValue, request: "", song: draft)
                    } catch {
                        problem = L10n.t("listen_ai_failed", error.localizedDescription)
                    }
                }
                if !artist.isEmpty { song = Bridge.shared.named(song: song, title: song.title, artist: artist) }
                let finished = song
                let notice = problem
                await MainActor.run {
                    let saved = model.save(finished)
                    step = nil
                    if let notice { model.message = notice }
                    path.removeLast()
                    path.append(.song(saved.id))
                }
            } catch {
                await MainActor.run { step = nil; self.error = error.localizedDescription }
            }
        }
    }
}
