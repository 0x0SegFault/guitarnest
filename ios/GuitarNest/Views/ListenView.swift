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

    private let aiReady = AIConnection.load().configured

    var body: some View {
        Form {
            Section {
                Text(L10n.t("listen_help")).font(.callout)
                TextField(L10n.t("title"), text: $title)
                Picker(L10n.t("level"), selection: $level) { ForEach(0..<3, id: \.self) { Text(levelName($0)).tag($0) } }
                    .pickerStyle(.segmented)
                Toggle(L10n.t(aiReady ? "improve_ai" : "improve_ai_off"), isOn: $refine).disabled(!aiReady)
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
            if title.isEmpty { title = url.deletingPathExtension().lastPathComponent }
            step = L10n.t("step_reading")
            Task.detached {
                do {
                    let audio = try AudioInput.decode(url)
                    await MainActor.run { make(audio) }
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

    private func make(_ audio: [Float]) {
        guard audio.count >= Int(AudioInput.rate) * 3 else {
            error = L10n.t("listen_too_short")
            step = nil
            return
        }
        step = L10n.t("step_listening")
        progress = 0
        let levelValue = Bridge.shared.level(index: Int32(level))
        let name = title
        let useAI = refine && aiReady
        Task.detached(priority: .userInitiated) {
            do {
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
                var aiProblem: String?
                if useAI {
                    await MainActor.run { step = L10n.t("step_asking") }
                    do {
                        song = try await AIClient.makeSong(AIConnection.load(), taskIndex: 3, level: levelValue, request: "", song: draft)
                    } catch {
                        aiProblem = error.localizedDescription
                    }
                }
                let finished = song
                await MainActor.run {
                    let saved = model.save(finished)
                    step = nil
                    if let aiProblem { model.message = L10n.t("listen_ai_failed", aiProblem) }
                    path.removeLast()
                    path.append(.song(saved.id))
                }
            } catch {
                await MainActor.run { step = nil; self.error = error.localizedDescription }
            }
        }
    }
}
