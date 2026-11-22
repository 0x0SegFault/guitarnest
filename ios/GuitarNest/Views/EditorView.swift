import GuitarNestShared
import SwiftUI
import UIKit

struct EditorView: View {
    @EnvironmentObject var model: AppModel
    let songId: String
    var seedTitle = ""
    var seedArtist = ""
    @Binding var path: [Route]

    @State private var loaded = false
    @State private var title = ""
    @State private var artist = ""
    @State private var source = ""
    @State private var level = 1
    @State private var tuning = "standard"
    @State private var capo = 0
    @State private var tempo = 90
    @State private var beatsPerBar = 4
    @State private var patterns: [PatternDraft] = []
    @State private var sections: [Draft] = []
    @State private var notes = ""
    @State private var error: String?

    struct PatternDraft: Identifiable { let id: String; var name: String; var steps: String; var perBeat: Int }
    struct Draft: Identifiable { let id = UUID(); var name: String; var repeats: Int; var strum: String?; var text: String }

    var body: some View {
        Form {
            if let error { Text(error).foregroundStyle(Palette.off) }
            Section {
                TextField(L10n.t("title"), text: $title)
                TextField(L10n.t("artist"), text: $artist)
                TextField(L10n.t("tab_by"), text: $source)
                Picker(L10n.t("level"), selection: $level) {
                    ForEach(0..<3, id: \.self) { Text(levelName($0)).tag($0) }
                }
                Picker(L10n.t("tuning"), selection: $tuning) {
                    ForEach(Bridge.shared.tuningPresets(), id: \.id) { t in Text(tuningLabel(t)).tag(t.id) }
                }
                Stepper("\(L10n.t("capo")): \(capo)", value: $capo, in: 0...12)
                Stepper("\(L10n.t("tempo_bpm")): \(tempo)", value: $tempo, in: 20...300)
                Stepper("\(L10n.t("beats_per_bar")): \(beatsPerBar)", value: $beatsPerBar, in: 1...12)
            }
            Section(L10n.t("strumming_patterns")) {
                ForEach($patterns) { $p in
                    HStack {
                        TextField(L10n.t("name"), text: $p.name).frame(maxWidth: 110)
                        TextField("D U x -", text: $p.steps)
                            .font(.system(.body, design: .monospaced))
                            .textInputAutocapitalization(.characters)
                            .onChange(of: p.steps) { _, value in
                                let clean = String(value.uppercased().replacingOccurrences(of: "X", with: "x").filter { "DUx-".contains($0) })
                                if clean != value { p.steps = clean }
                            }
                    }
                }
                .onDelete { patterns.remove(atOffsets: $0) }
                ForEach(Bridge.shared.presetPatterns().filter { preset in !patterns.contains { $0.id == preset.id } }, id: \.id) { preset in
                    Button("+ \(preset.name)") { patterns.append(PatternDraft(id: preset.id, name: preset.name, steps: preset.steps, perBeat: Int(preset.perBeat))) }
                }
            }
            Section {
                Text(L10n.t("section_help")).font(.caption).foregroundStyle(.secondary)
                ForEach($sections) { $d in
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            TextField(L10n.t("name"), text: $d.name)
                            Stepper("×\(d.repeats)", value: $d.repeats, in: 1...16).fixedSize()
                        }
                        Picker(L10n.t("strumming_pattern"), selection: $d.strum) {
                            Text(L10n.t("none")).tag(String?.none)
                            ForEach(patterns) { p in Text(p.name).tag(String?.some(p.id)) }
                        }
                        TabText(text: $d.text).frame(minHeight: 130)
                        if let problem = Bridge.shared.sectionProblem(text: d.text, beatsPerBar: Int32(beatsPerBar)) {
                            Text(problem).font(.caption).foregroundStyle(Palette.off)
                        }
                    }
                }
                .onDelete { sections.remove(atOffsets: $0) }
                Button(L10n.t("add_section")) {
                    sections.append(Draft(name: "Section \(sections.count + 1)", repeats: 1, strum: patterns.first?.id, text: "| G | C | D | G |"))
                }
            } header: { Text(L10n.t("sections")) }
            Section(L10n.t("notes")) { TextField(L10n.t("notes"), text: $notes, axis: .vertical) }
        }
        .navigationTitle(L10n.t(songId.isEmpty ? "new_song" : "edit_song"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { Button(L10n.t("save")) { save() } }
        .onAppear(perform: load)
    }

    private func load() {
        guard !loaded else { return }
        loaded = true
        let song = model.song(id: songId) ?? Bridge.shared.createSong(title: seedTitle)
        title = song.title
        artist = songId.isEmpty ? seedArtist : song.artist
        source = song.source
        level = Int(Bridge.shared.levelIndex(level: song.level))
        tuning = song.tuning
        capo = Int(song.capo)
        tempo = Int(song.tempo)
        beatsPerBar = Int(song.beatsPerBar)
        patterns = song.patterns.map { PatternDraft(id: $0.id, name: $0.name, steps: $0.steps, perBeat: Int($0.perBeat)) }
        sections = Bridge.shared.drafts(song: song).map { Draft(name: $0.name, repeats: Int(Bridge.shared.draftRepeat(draft: $0)), strum: $0.strum, text: $0.text) }
        notes = song.notes
    }

    private func save() {
        let base = model.song(id: songId) ?? Bridge.shared.createSong(title: title)
        let result = Bridge.shared.build(
            base: base, title: title, artist: artist, source: source, level: Bridge.shared.level(index: Int32(level)), tuning: tuning,
            capo: Int32(capo), tempo: Int32(tempo), beatsPerBar: Int32(beatsPerBar),
            patterns: patterns.map { Bridge.shared.pattern(id: $0.id, name: $0.name, steps: $0.steps, perBeat: Int32($0.perBeat)) },
            sections: sections.map { Bridge.shared.draft(name: $0.name, repeats: Int32($0.repeats), strum: $0.strum, text: $0.text) },
            notes: notes
        )
        guard let song = result.song else {
            error = result.error
            return
        }
        let saved = model.save(song)
        path.removeLast()
        if songId.isEmpty { path.append(.song(saved.id)) }
    }
}

/// Tab text: monospace, no line wrapping (each string stays on one line), scrolls sideways.
struct TabText: UIViewRepresentable {
    @Binding var text: String

    func makeUIView(context: Context) -> UITextView {
        let view = UITextView()
        view.font = .monospacedSystemFont(ofSize: 13, weight: .regular)
        view.autocorrectionType = .no
        view.autocapitalizationType = .none
        view.smartQuotesType = .no
        view.smartDashesType = .no
        view.textContainer.widthTracksTextView = false
        view.textContainer.size = CGSize(width: 4_000, height: CGFloat.greatestFiniteMagnitude)
        view.textContainer.lineBreakMode = .byClipping
        view.isScrollEnabled = true
        view.alwaysBounceHorizontal = true
        view.backgroundColor = .secondarySystemBackground
        view.layer.cornerRadius = 8
        view.delegate = context.coordinator
        return view
    }

    func updateUIView(_ view: UITextView, context: Context) {
        if view.text != text { view.text = text }
    }

    func makeCoordinator() -> Coordinator { Coordinator(text: $text) }

    final class Coordinator: NSObject, UITextViewDelegate {
        let text: Binding<String>
        init(text: Binding<String>) { self.text = text }
        func textViewDidChange(_ view: UITextView) { text.wrappedValue = view.text }
    }
}
