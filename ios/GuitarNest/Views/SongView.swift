import GuitarNestShared
import SwiftUI
import UIKit

struct SongView: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var player: MetronomePlayer
    let songId: String
    @Binding var path: [Route]
    @State private var tempoPercent = 100
    @State private var trainer = false
    @State private var loop: Int?
    @State private var playingId: String?
    @State private var started = Date()
    @State private var best = 0
    @State private var asking = false
    @State private var confirmDelete = false
    @State private var exportURL: URL?

    var body: some View {
        if let song = model.song(id: songId) {
            content(song)
        } else {
            Text(L10n.t("no_results")).foregroundStyle(.secondary)
        }
    }

    private func content(_ song: Song) -> some View {
        let order = playOrder(song)
        let mine = playingId == song.id ? player.position : nil
        let current: PlayedBar? = mine.flatMap { p in p.bar >= 1 && !order.isEmpty ? order[(p.bar - 1) % order.count] : nil }
        return VStack(spacing: 0) {
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        header(song)
                        ForEach(Array(song.sections.enumerated()), id: \.offset) { s, section in
                            HStack(spacing: 8) {
                                Text(section.name).font(.headline)
                                let repeats = Int(Bridge.shared.sectionRepeat(section: section))
                                if repeats > 1 { Text("×\(repeats)").foregroundStyle(.secondary) }
                                if let strum = section.strum, let p = song.pattern(id: strum) { Text(p.name).font(.caption).foregroundStyle(.secondary) }
                            }
                            LazyVGrid(columns: [GridItem(.adaptive(minimum: 160), spacing: 8)], alignment: .leading, spacing: 8) {
                                ForEach(Array(section.bars.enumerated()), id: \.offset) { b, bar in
                                    let active = current.map { Int($0.section) == s && Int($0.bar) == b } ?? false
                                    let pattern = song.pattern(id: bar.strum ?? section.strum ?? "")
                                    BarCard(song: song, section: s, bar: b, pattern: pattern, active: active, step: active ? mine?.step : nil)
                                        .id("\(s)-\(b)")
                                }
                            }
                        }
                        if !song.notes.isEmpty { Text(song.notes).font(.body) }
                        Link(L10n.t("find_tabs_browser"), destination: searchURL(song))
                    }
                    .padding()
                }
                .onChange(of: current.map { "\($0.section)-\($0.bar)" }) { _, key in
                    if let key { withAnimation { proxy.scrollTo(key, anchor: .center) } }
                }
            }
            controls(song, mine)
        }
        .navigationTitle(song.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            Menu {
                Button(L10n.t("ask_ai")) { player.stop(); asking = true }
                Button(L10n.t("edit")) { player.stop(); path.append(.edit(song.id)) }
                Button(L10n.t("print")) { printSong(song) }
                Button(L10n.t("export_file")) { exportURL = export(song) }
                Button(L10n.t("delete"), role: .destructive) { confirmDelete = true }
            } label: { Image(systemName: "ellipsis.circle") }
            .accessibilityLabel(L10n.t("more"))
        }
        .sheet(isPresented: $asking) {
            NavigationStack { AskAIView(song: song) { made in asking = false; path.append(.song(made.id)) } }
        }
        .sheet(item: Binding(get: { exportURL.map { ExportFile(url: $0) } }, set: { exportURL = $0?.url })) { file in
            ShareSheet(items: [file.url])
        }
        .confirmationDialog(L10n.t("delete_song_title", song.title), isPresented: $confirmDelete, titleVisibility: .visible) {
            Button(L10n.t("delete"), role: .destructive) { player.stop(); model.delete(song); path.removeLast() }
        } message: { Text(L10n.t("delete_song_text")) }
        .onDisappear { stop() }
        .onAppear { if Launch.play && playingId == nil { play(song) } }
    }

    private func header(_ song: Song) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            if !song.artist.isEmpty { Text(song.artist).font(.headline).foregroundStyle(.secondary) }
            Text(facts(song)).font(.subheadline)
            if !song.source.isEmpty { Text(L10n.t("tab_source", song.source)).font(.caption).foregroundStyle(.secondary) }
            let sessions = model.practice.filter { $0.songId == song.id }
            if !sessions.isEmpty {
                Text(L10n.t("practice_summary", sessions.count, sessions.reduce(0) { $0 + Int($1.seconds) } / 60, sessions.map { Int($0.bestTempo) }.max() ?? 0))
                    .font(.caption).foregroundStyle(Palette.inTune)
            }
            ForEach(song.patterns, id: \.id) { p in
                HStack { Text(p.name).font(.subheadline.bold()); StrumRow(steps: p.steps, perBeat: Int(p.perBeat)) }
            }
            Divider()
        }
    }

    private func facts(_ song: Song) -> String {
        var parts: [String] = []
        if let t = song.tuningNotes() { parts.append(tuningLabel(t)) }
        parts.append(song.capo > 0 ? L10n.t("capo_n", Int(song.capo)) : L10n.t("no_capo"))
        parts.append("\(song.tempo) BPM · \(song.beatsPerBar)/4")
        parts.append(levelName(Int(Bridge.shared.levelIndex(level: song.level))))
        return parts.joined(separator: "  ·  ")
    }

    private func controls(_ song: Song, _ mine: MetronomePlayer.Position?) -> some View {
        HStack(spacing: 8) {
            if mine == nil {
                Button { play(song) } label: { Label(L10n.t("play_along"), systemImage: "play.fill") }
                    .buttonStyle(.borderedProminent)
                Menu(trainer ? L10n.t("speed_up") : "\(tempoPercent) %") {
                    ForEach([50, 70, 85, 100], id: \.self) { p in Button("\(p) %") { trainer = false; tempoPercent = p } }
                    Button(L10n.t("speed_up")) { trainer = true }
                }
                .buttonStyle(.bordered)
                if song.sections.count > 1 {
                    Menu(loop.map { L10n.t("loop_section", song.sections[$0].name) } ?? L10n.t("whole_song")) {
                        Button(L10n.t("whole_song")) { loop = nil }
                        ForEach(Array(song.sections.enumerated()), id: \.offset) { i, s in Button(L10n.t("loop_section", s.name)) { loop = i } }
                    }
                    .buttonStyle(.bordered)
                }
            } else if let mine {
                Button { stop() } label: { Label(L10n.t("stop"), systemImage: "stop.fill") }
                    .buttonStyle(.borderedProminent)
                Text(mine.bar < 1 ? L10n.t("count_in") : "\(mine.bpm) BPM · " + L10n.t("bar_n", mine.bar)).font(.headline)
            }
            Spacer()
        }
        .padding(12)
        .background(.bar)
    }

    private func playOrder(_ song: Song) -> [PlayedBar] {
        song.playOrder().filter { loop == nil || Int($0.section) == loop }
    }

    private func play(_ song: Song) {
        let order = playOrder(song)
        guard !order.isEmpty else { return }
        let base = Double(max(30, min(250, Int(song.tempo) * tempoPercent / 100)))
        let trainerPlan = Bridge.shared.speedTrainer(start: Int32(max(30, Int(Double(song.tempo) * 0.6))), target: song.tempo, step: 5, barsPerStep: Int32(min(8, order.count)))
        var plan: [Double] = []
        // The tempo per bar is computed here, so the audio thread only reads an array.
        for bar in 0..<2_000 {
            plan.append(trainer ? Double(trainerPlan.tempoForBar(bar: Int32(max(0, bar - 1)))) : base)
        }
        let looping = loop != nil
        let count = order.count
        player.tempoForBar = { bar in
            if !looping && bar - 1 >= count { return nil }
            return plan[min(bar, plan.count - 1)]
        }
        player.onEnd = { stop() }
        playingId = song.id
        started = Date()
        best = Int(base)
        player.start(beatsPerBar: Int(song.beatsPerBar), subdivisions: 1)
    }

    private func stop() {
        guard let id = playingId else { return }
        best = max(best, player.position?.bpm ?? 0)
        player.stop()
        playingId = nil
        model.logPractice(songId: id, seconds: Int(Date().timeIntervalSince(started)), bestTempo: best)
    }

    private func searchURL(_ song: Song) -> URL {
        let query = "\(song.artist) \(song.title) guitar tab".addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        return URL(string: "https://duckduckgo.com/?q=\(query)") ?? URL(string: "https://duckduckgo.com")!
    }

    private func printSong(_ song: Song) {
        let formatter = UISimpleTextPrintFormatter(text: Bridge.shared.printText(song: song))
        formatter.font = .monospacedSystemFont(ofSize: 7.5, weight: .regular)
        formatter.perPageContentInsets = UIEdgeInsets(top: 36, left: 36, bottom: 36, right: 36)
        let controller = UIPrintInteractionController.shared
        let info = UIPrintInfo(dictionary: nil)
        info.jobName = song.title
        info.outputType = .grayscale
        controller.printInfo = info
        controller.printFormatter = formatter
        controller.present(animated: true)
    }

    private func export(_ song: Song) -> URL? {
        let name = song.title.components(separatedBy: CharacterSet.alphanumerics.union(.whitespaces).inverted).joined()
        let url = FileManager.default.temporaryDirectory.appendingPathComponent((name.isEmpty ? "song" : name) + ".gnt")
        try? Bridge.shared.encodeSong(song: song).write(to: url, atomically: true, encoding: .utf8)
        return url
    }
}

struct ExportFile: Identifiable {
    let url: URL
    var id: String { url.path }
}

struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController { UIActivityViewController(activityItems: items, applicationActivities: nil) }
    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

/// One bar: chords, the six strings (when the bar has notes) and its strumming pattern.
struct BarCard: View {
    let song: Song
    let section: Int
    let bar: Int
    let pattern: StrumPattern?
    let active: Bool
    let step: Int?

    var body: some View {
        let content = song.sections[section].bars[bar]
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 8) {
                Text("\(bar + 1)").font(.caption2).foregroundStyle(.secondary)
                if !content.chords.isEmpty {
                    ForEach(Array(Bridge.shared.chordCells(song: song, section: Int32(section), bar: Int32(bar)).enumerated()), id: \.offset) { _, c in
                        Text(c == "." ? "·" : c).font(.headline).foregroundStyle(c == "." ? .secondary : .primary)
                    }
                }
            }
            if !content.beats.isEmpty {
                Text(Bridge.shared.barLines(song: song, section: Int32(section), bar: Int32(bar)))
                    .font(.system(size: 10, design: .monospaced))
                    .fixedSize(horizontal: true, vertical: false)
            }
            if let pattern {
                StrumRow(steps: pattern.steps, perBeat: Int(pattern.perBeat), active: step.map { $0 * Int(pattern.perBeat) / 4 })
            }
        }
        .padding(8)
        .background(RoundedRectangle(cornerRadius: 12).fill(active ? Palette.primaryContainer : Palette.card))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(active ? Palette.primary : Palette.outlineVariant, lineWidth: active ? 2 : 1))
    }
}

func tuningLabel(_ t: Tuning) -> String {
    let names: [String: String] = [
        "standard": "tuning_standard", "half_step_down": "tuning_half_step_down", "whole_step_down": "tuning_whole_step_down",
        "drop_d": "tuning_drop_d", "drop_c": "tuning_drop_c", "open_d": "tuning_open_d", "open_g": "tuning_open_g", "open_e": "tuning_open_e"
    ]
    let name = t.id == "dadgad" ? "DADGAD" : (names[t.id].map { L10n.t($0) } ?? t.id)
    return name + " · " + t.names.map { String($0.dropLast()) }.joined(separator: " ")
}
