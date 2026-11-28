import GuitarNestShared
import SwiftUI
import UniformTypeIdentifiers

struct LibraryView: View {
    @EnvironmentObject var model: AppModel
    @Binding var path: [Route]
    @State private var query = ""
    @State private var importing = false
    @State private var sheet: Sheet?

    enum Sheet: String, Identifiable {
        case ai, ask, search, songBook, backup, report, myReports, about
        var id: String { rawValue }
    }

    private var shown: [Song] {
        model.songs.filter { query.isEmpty || $0.title.localizedCaseInsensitiveContains(query) || $0.artist.localizedCaseInsensitiveContains(query) }
    }

    var body: some View {
        List {
            if model.songs.isEmpty {
                Text(L10n.t("library_empty")).foregroundStyle(.secondary)
            }
            ForEach(shown, id: \.id) { song in
                NavigationLink(value: Route.song(song.id)) { SongRow(song: song) }
            }
        }
        .listStyle(.plain)
        .searchable(text: $query, prompt: L10n.t("search_library"))
        .navigationTitle("GuitarNest")
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button { sheet = .songBook } label: { Image(systemName: "book") }
                    .accessibilityLabel(L10n.t("song_book"))
                Menu {
                    Button(L10n.t("listen_title")) { path.append(.listen) }
                    Button(L10n.t("write_tab")) { path.append(.edit("")) }
                    Button(L10n.t("find_song")) { sheet = .search }
                    Button(L10n.t("ai_new_tab")) { sheet = .ask }
                    Button(L10n.t("import_file")) { importing = true }
                } label: { Image(systemName: "plus") }
                .accessibilityLabel(L10n.t("new_song"))
                Menu {
                    Button(L10n.t("ai_connection")) { sheet = .ai }
                    Button(L10n.t("backup_title")) { sheet = .backup }
                    Button(L10n.t("report_title")) { sheet = .report }
                    Button(L10n.t("my_reports")) { sheet = .myReports }
                    Button(L10n.t("about")) { sheet = .about }
                } label: { Image(systemName: "gearshape") }
                .accessibilityLabel(L10n.t("settings"))
            }
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.json, .xml, .midi, .data, .item]) { result in
            guard case .success(let url) = result else { return }
            do {
                let song = try model.importFile(url)
                path.append(.song(song.id))
            } catch {
                model.message = error.localizedDescription
            }
        }
        .sheet(item: $sheet) { which in
            NavigationStack {
                switch which {
                case .ai: AIConnectionView()
                case .ask: AskAIView(song: nil) { made in sheet = nil; path.append(.song(made.id)) }
                case .search: SearchView(onPick: { info in
                    sheet = nil
                    path.append(.newSong(title: info.title, artist: info.artist))
                }, onMade: { made in
                    sheet = nil
                    path.append(.song(made.id))
                })
                case .songBook: SongBookView { made in
                    sheet = nil
                    path.append(.song(made.id))
                }
                case .backup: BackupView()
                case .report: ReportView()
                case .myReports: MyReportsView()
                case .about: AboutView()
                }
            }
        }
        .onAppear {
            if Launch.openExample, path.isEmpty, let first = model.songs.first { path.append(.song(first.id)) }
        }
    }
}

struct SongRow: View {
    @EnvironmentObject var model: AppModel
    let song: Song

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(song.title).font(.headline)
            Text(details).font(.caption).foregroundStyle(.secondary)
            if let last = model.practice.filter({ $0.songId == song.id }).map({ $0.day }).max() {
                let days = Int(L10n.today() - last)
                Text(days == 0 ? L10n.t("practised_today") : String.localizedStringWithFormat(NSLocalizedString("practised_days_ago", comment: ""), days))
                    .font(.caption).foregroundStyle(Palette.inTune)
            }
        }
        .padding(.vertical, 4)
    }

    private var details: String {
        var parts: [String] = []
        if !song.artist.isEmpty { parts.append(song.artist) }
        parts.append(levelName(Int(Bridge.shared.levelIndex(level: song.level))))
        if song.capo > 0 { parts.append(L10n.t("capo_n", Int(song.capo))) }
        parts.append("\(song.tempo) BPM")
        return parts.joined(separator: " · ")
    }
}

func levelName(_ index: Int) -> String {
    [L10n.t("level_beginner"), L10n.t("level_intermediate"), L10n.t("level_advanced")][max(0, min(2, index))]
}
