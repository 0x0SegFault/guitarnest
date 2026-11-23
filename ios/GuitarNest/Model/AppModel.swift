import Foundation
import GuitarNestShared

/// The library on this iPhone: one .gnt file per song, plus the practice log. Excluded from iCloud backup;
/// the player makes encrypted backups when they want (Backup and restore).
@MainActor
final class AppModel: ObservableObject {
    @Published private(set) var songs: [Song] = []
    @Published private(set) var practice: [PracticeEntry] = []
    @Published var message: String?

    let demo = ProcessInfo.processInfo.arguments.contains("--demo")
    private let folder: URL
    private let practiceFile: URL

    init() {
        let base = (try? FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true))
            ?? FileManager.default.temporaryDirectory
        folder = base.appendingPathComponent("songs", isDirectory: true)
        practiceFile = base.appendingPathComponent("practice.json")
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        var baseURL = base
        try? baseURL.setResourceValues(values)
        load()
        let seeded = base.appendingPathComponent(".seeded")
        if songs.isEmpty && !FileManager.default.fileExists(atPath: seeded.path) {
            save(Bridge.shared.exampleSong())
            FileManager.default.createFile(atPath: seeded.path, contents: Data())
        }
    }

    func load() {
        let files = (try? FileManager.default.contentsOfDirectory(at: folder, includingPropertiesForKeys: nil)) ?? []
        songs = files.filter { $0.pathExtension == "gnt" }.compactMap { url in
            guard let text = try? String(contentsOf: url, encoding: .utf8) else { return nil }
            return try? Bridge.shared.decodeSong(text: text)
        }.sorted { $0.title.lowercased() < $1.title.lowercased() }
        if let data = try? Data(contentsOf: practiceFile), let text = String(data: data, encoding: .utf8),
           let content = try? Bridge.shared.backupDecode(text: text) {
            practice = content.practice
        }
    }

    @discardableResult
    func save(_ song: Song) -> Song {
        let saved = song.id.isEmpty ? Bridge.shared.withId(song: song, id: UUID().uuidString.lowercased()) : song
        let url = folder.appendingPathComponent(saved.id + ".gnt")
        try? Bridge.shared.encodeSong(song: saved).write(to: url, atomically: true, encoding: .utf8)
        load()
        return saved
    }

    func delete(_ song: Song) {
        try? FileManager.default.removeItem(at: folder.appendingPathComponent(song.id + ".gnt"))
        load()
    }

    func song(id: String) -> Song? {
        songs.first { $0.id == id }
    }

    /// Imports .gnt or MusicXML. A song with an id already in the library is kept as a copy.
    func importFile(_ url: URL) throws -> Song {
        let access = url.startAccessingSecurityScopedResource()
        defer { if access { url.stopAccessingSecurityScopedResource() } }
        let data = try Data(contentsOf: url)
        if data.starts(with: [0x50, 0x4B]) { throw AppError(L10n.t("ios_mxl_not_supported")) }
        var song = try Bridge.shared.importBytes(bytes: Convert.bytes(data), name: url.deletingPathExtension().lastPathComponent, source: "Imported file")
        if !song.id.isEmpty && self.song(id: song.id) != nil { song = Bridge.shared.withId(song: song, id: "") }
        return save(song)
    }

    func logPractice(songId: String, seconds: Int, bestTempo: Int) {
        guard seconds >= 10 else { return }
        practice.append(Bridge.shared.practiceEntry(songId: songId, day: L10n.today(), seconds: Int32(seconds), bestTempo: Int32(bestTempo)))
        writePractice()
    }

    func backupText() -> String {
        Bridge.shared.backupEncode(songs: songs, practice: practice)
    }

    /// Restores by merging; returns the number of songs.
    func restore(_ text: String) throws -> Int {
        let content = try Bridge.shared.backupDecode(text: text)
        for song in content.songs { save(song) }
        practice = Bridge.shared.mergePractice(current: practice, incoming: content.practice)
        writePractice()
        return content.songs.count
    }

    private func writePractice() {
        try? Bridge.shared.encodePractice(entries: practice).write(to: practiceFile, atomically: true, encoding: .utf8)
    }
}

struct AppError: LocalizedError {
    let text: String
    init(_ text: String) { self.text = text }
    var errorDescription: String? { text }
}
