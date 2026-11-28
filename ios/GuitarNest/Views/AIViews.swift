import GuitarNestShared
import SwiftUI

/// A provider preset: suggested model is empty when the player types it. Home-network addresses are examples to edit.
private struct Preset {
    let name: String
    let url: String
    let model: String
}

private let presets = [
    Preset(name: "Anthropic", url: "https://api.anthropic.com/v1", model: "claude-sonnet-5-5"),
    Preset(name: "OpenAI", url: "https://api.openai.com/v1", model: ""),
    Preset(name: "Mistral", url: "https://api.mistral.ai/v1", model: "mistral-large-latest"),
    Preset(name: "OpenRouter", url: "https://openrouter.ai/api/v1", model: ""),
    Preset(name: "LiteLLM", url: "https://litellm.example.org/v1", model: ""),
    Preset(name: "Ollama", url: "http://192.168.1.10:11434/v1", model: ""),
    Preset(name: "LM Studio", url: "http://192.168.1.10:1234/v1", model: "")
]

struct AIConnectionView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var connection = AIConnection.load()
    @State private var status: String?
    @State private var working = false
    @State private var signIn = OpenRouterSignIn()

    var body: some View {
        Form {
            Section {
                Text(L10n.t("ai_connection_help")).font(.callout)
                Button(L10n.t("openrouter_sign_in")) { openRouter() }.buttonStyle(.borderedProminent).disabled(working)
                Text(L10n.t("openrouter_help")).font(.caption).foregroundStyle(.secondary)
            }
            Section(L10n.t("ai_or_manual")) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack {
                        ForEach(presets, id: \.name) { preset in
                            Button(preset.name) {
                                connection.baseUrl = preset.url
                                if !preset.model.isEmpty { connection.model = preset.model }
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                }
                TextField(L10n.t("ai_address"), text: $connection.baseUrl).keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
                if connection.unencrypted { Text(L10n.t("ai_http_warning")).font(.caption).foregroundStyle(Palette.off) }
                TextField(L10n.t("ai_model"), text: $connection.model).textInputAutocapitalization(.never).autocorrectionDisabled()
                SecureField(L10n.t("ai_key"), text: $connection.apiKey)
                Text(L10n.t("ai_privacy")).font(.caption).foregroundStyle(.secondary)
            }
            if working { ProgressView() }
            if let status { Text(status) }
            Section {
                Button(L10n.t("ai_test")) { test() }.disabled(!connection.configured || working)
                Button(L10n.t("save")) { connection.save(); dismiss() }.disabled(!connection.configured)
                if AIConnection.load().configured {
                    Button(L10n.t("ai_remove"), role: .destructive) { AIConnection.clear(); connection = AIConnection.load() }
                }
            }
        }
        .navigationTitle(L10n.t("ai_connection"))
        .navigationBarTitleDisplayMode(.inline)
    }

    private func openRouter() {
        working = true
        status = nil
        Task {
            do {
                let key = try await signIn.signIn()
                let model = connection.baseUrl == OpenRouterSignIn.baseUrl && !connection.model.isEmpty ? connection.model : OpenRouterSignIn.defaultModel
                connection = AIConnection(baseUrl: OpenRouterSignIn.baseUrl, model: model, apiKey: key)
                connection.save()
                status = L10n.t("openrouter_signed_in")
            } catch {
                status = error.localizedDescription
            }
            working = false
        }
    }

    private func test() {
        working = true
        status = nil
        let c = connection
        Task {
            do {
                let answer = try await AIClient.chat(c, Bridge.shared.aiTestRequest(model: c.model))
                status = L10n.t("ai_test_ok", String(answer.prefix(80)))
            } catch {
                status = error.localizedDescription
            }
            working = false
        }
    }
}

struct AskAIView: View {
    @EnvironmentObject var model: AppModel
    let song: Song?
    let onMade: (Song) -> Void
    @State private var task = 0
    @State private var level = 0
    @State private var request = ""
    @State private var working = false
    @State private var error: String?
    @State private var setup = false
    private let connection = AIConnection.load()

    var body: some View {
        Form {
            if !connection.configured {
                Text(L10n.t("ai_not_set"))
                Button(L10n.t("ai_connection")) { setup = true }
            } else {
                if song != nil {
                    Picker("", selection: $task) {
                        Text(L10n.t("ai_arrange")).tag(0)
                        Text(L10n.t("ai_strumming")).tag(1)
                    }
                    .pickerStyle(.segmented)
                }
                if task != 1 {
                    Picker(L10n.t("level"), selection: $level) { ForEach(0..<3, id: \.self) { Text(levelName($0)).tag($0) } }.pickerStyle(.segmented)
                }
                Section(L10n.t("ai_request")) {
                    TextField(L10n.t(song == nil ? "ai_request_hint_new" : "ai_request_hint"), text: $request, axis: .vertical).lineLimit(3...6)
                }
                Text(L10n.t("ai_sends", connection.model, URL(string: connection.baseUrl)?.host ?? connection.baseUrl)).font(.caption).foregroundStyle(.secondary)
                if working { ProgressView(L10n.t("working")) }
                if let error { Text(error).foregroundStyle(Palette.off) }
                Button(L10n.t("ai_make")) { make() }.disabled(working || (song == nil && request.isEmpty))
            }
        }
        .navigationTitle(L10n.t(song == nil ? "ai_new_tab" : "ask_ai"))
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $setup) { NavigationStack { AIConnectionView() } }
        .onAppear { if let song { level = Int(Bridge.shared.levelIndex(level: song.level)) } }
    }

    private func make() {
        working = true
        error = nil
        // Task index in the shared enum: 0 arrange, 1 strumming, 2 write, 3 refine.
        let index = song == nil ? 2 : task
        Task {
            do {
                let made = try await AIClient.makeSong(connection, taskIndex: index, level: Bridge.shared.level(index: Int32(level)), request: request, song: song)
                onMade(model.save(made))
            } catch {
                self.error = error.localizedDescription
            }
            working = false
        }
    }
}

struct SearchView: View {
    @EnvironmentObject var model: AppModel
    let onPick: (SongInfo) -> Void
    let onMade: (Song) -> Void
    private let connection = AIConnection.load()
    @State private var query = ""
    @State private var results: [SongInfo]?
    @State private var scores: [FreeScore] = []
    @State private var working = false
    @State private var error: String?

    var body: some View {
        List {
            Section {
                TextField(L10n.t("find_song_hint"), text: $query).onSubmit(search)
                Button(L10n.t("search"), action: search).disabled(query.isEmpty || working)
                Text(L10n.t("find_song_privacy")).font(.caption).foregroundStyle(.secondary)
            }
            if working { ProgressView() }
            if let error { Text(error).foregroundStyle(Palette.off) }
            if !scores.isEmpty {
                Section {
                    Text(L10n.t("free_scores_help")).font(.caption).foregroundStyle(.secondary)
                    ForEach(Array(scores.enumerated()), id: \.offset) { _, s in
                        Button { importScore(s) } label: {
                            VStack(alignment: .leading) {
                                Text(s.title).font(.headline).foregroundStyle(.primary)
                                Text([s.composer, s.opus, s.licence].filter { !$0.isEmpty }.joined(separator: " · "))
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                        }
                        .disabled(working)
                    }
                } header: { Text(L10n.t("free_scores")) }
            }
            if let results {
                if results.isEmpty {
                    Text(L10n.t("no_results"))
                } else {
                    Text(L10n.t(connection.configured ? "search_pick_help_ai" : "search_pick_help")).font(.caption).foregroundStyle(.secondary)
                }
                ForEach(Array(results.enumerated()), id: \.offset) { _, r in
                    HStack {
                        Button { onPick(r) } label: {
                            VStack(alignment: .leading) {
                                Text(r.title).font(.headline).foregroundStyle(.primary)
                                Text([r.artist, r.album, r.year, r.seconds.map { String(format: "%d:%02d", Int(truncating: $0) / 60, Int(truncating: $0) % 60) } ?? ""]
                                    .filter { !$0.isEmpty }.joined(separator: " · ")).font(.caption).foregroundStyle(.secondary)
                            }
                        }
                        .buttonStyle(.plain)
                        Spacer()
                        if connection.configured {
                            Button(L10n.t("ai_chords")) { chords(r) }.buttonStyle(.borderless).disabled(working)
                        }
                    }
                }
            }
        }
        .navigationTitle(L10n.t("find_song"))
        .navigationBarTitleDisplayMode(.inline)
    }

    /// Your AI writes the chords and strumming of the song: no lyrics, no melody.
    private func chords(_ info: SongInfo) {
        working = true
        error = nil
        let request = [info.title, info.artist].filter { !$0.isEmpty }.joined(separator: " – ")
        Task {
            do {
                let made = try await AIClient.makeSong(connection, taskIndex: AIClient.chordsTask,
                                                       level: Bridge.shared.level(index: 1), request: request, song: nil)
                onMade(model.save(made))
            } catch {
                self.error = error.localizedDescription
            }
            working = false
        }
    }

    /// Downloads the free score and opens it as a new song.
    private func importScore(_ score: FreeScore) {
        working = true
        error = nil
        Task {
            do {
                let data = try await AIClient.scoreMidi(score)
                let song = try Bridge.shared.freeScoreSong(score: score, midi: Convert.bytes(data))
                onMade(model.save(song))
            } catch {
                self.error = error.localizedDescription
            }
            working = false
        }
    }

    private func search() {
        guard !query.isEmpty else { return }
        scores = Bridge.shared.freeScores(query: query)
        working = true
        error = nil
        let q = query
        Task {
            do { results = try await AIClient.search(q) } catch { self.error = error.localizedDescription }
            working = false
        }
    }
}
