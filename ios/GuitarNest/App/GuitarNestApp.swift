import GuitarNestShared
import SwiftUI

@main
struct GuitarNestApp: App {
    @StateObject private var model = AppModel()
    @StateObject private var player = MetronomePlayer()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .environmentObject(player)
                .tint(Palette.primary)
        }
    }
}

enum AppTab: Hashable { case songs, metronome, tuner }

struct RootView: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var player: MetronomePlayer
    @State private var tab: AppTab = Launch.tab
    @State private var path: [Route] = Launch.route

    var body: some View {
        TabView(selection: $tab) {
            NavigationStack(path: $path) {
                LibraryView(path: $path)
                    .navigationDestination(for: Route.self) { route in
                        switch route {
                        case .song(let id): SongView(songId: id, path: $path)
                        case .edit(let id): EditorView(songId: id, path: $path)
                        case .newSong(let title, let artist): EditorView(songId: "", seedTitle: title, seedArtist: artist, path: $path)
                        case .listen: ListenView(path: $path)
                        }
                    }
            }
            .tabItem { Label(L10n.t("songs"), systemImage: "music.note.list") }
            .tag(AppTab.songs)
            NavigationStack { MetronomeView() }
                .tabItem { Label(L10n.t("metronome"), systemImage: "metronome") }
                .tag(AppTab.metronome)
            NavigationStack { TunerView() }
                .tabItem { Label(L10n.t("tuner"), systemImage: "tuningfork") }
                .tag(AppTab.tuner)
        }
        .onChange(of: tab) { player.stop() }
        .alert(model.message ?? "", isPresented: Binding(get: { model.message != nil }, set: { if !$0 { model.message = nil } })) {
            Button("OK", role: .cancel) {}
        }
    }
}

enum Route: Hashable {
    case song(String)
    /// Song id, or "" for a new song.
    case edit(String)
    /// A new song with details from the song search.
    case newSong(title: String, artist: String)
    case listen
}

/// Launch arguments for CI screenshots: --tab metronome|tuner|listen, --open-example, --play, --demo-hz 110.1.
enum Launch {
    static let args = ProcessInfo.processInfo.arguments

    static var tab: AppTab {
        guard let i = args.firstIndex(of: "--tab"), i + 1 < args.count else { return .songs }
        switch args[i + 1] {
        case "metronome": return .metronome
        case "tuner": return .tuner
        default: return .songs
        }
    }

    static var route: [Route] {
        if args.contains("--tab"), let i = args.firstIndex(of: "--tab"), i + 1 < args.count, args[i + 1] == "listen" { return [.listen] }
        return []
    }

    static var play: Bool { args.contains("--play") }
    static var openExample: Bool { args.contains("--open-example") }
}
