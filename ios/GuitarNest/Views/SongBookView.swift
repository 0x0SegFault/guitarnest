import SwiftUI
import GuitarNestShared

/// Public-domain songs arranged for GuitarNest. Pick a level, then a song: it is added to your songs.
struct SongBookView: View {
    @EnvironmentObject var model: AppModel
    let onMade: (Song) -> Void
    @State private var level = 0
    private let books = Bridge.shared.songBook()

    var body: some View {
        List {
            Section {
                Text(L10n.t("song_book_help")).font(.caption).foregroundStyle(.secondary)
                Picker(L10n.t("level"), selection: $level) {
                    Text(L10n.t("level_beginner")).tag(0)
                    Text(L10n.t("level_intermediate")).tag(1)
                    Text(L10n.t("level_advanced")).tag(2)
                }
                .pickerStyle(.segmented)
                Text(L10n.t(["song_book_beginner", "song_book_intermediate", "song_book_advanced"][level])).font(.caption)
            }
            ForEach(Array(books.enumerated()), id: \.offset) { _, book in
                Button {
                    let song = Bridge.shared.songBookSong(book: book, level: Bridge.shared.level(index: Int32(level)))
                    onMade(model.save(song))
                } label: {
                    VStack(alignment: .leading) {
                        Text(book.title).font(.headline).foregroundStyle(.primary)
                        Text(book.origin).font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
        }
        .navigationTitle(L10n.t("song_book"))
        .navigationBarTitleDisplayMode(.inline)
    }
}
