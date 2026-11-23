import GuitarNestShared
import SwiftUI

/// Everything GuitarNest is built with (same list as CREDITS.md). Tap an entry to open its page.
struct CreditsView: View {
    var body: some View {
        List {
            Text(L10n.t("credits_intro")).font(.caption).foregroundStyle(.secondary)
            ForEach(Bridge.shared.creditSections(), id: \.key) { section in
                Section(L10n.t(section.key)) {
                    ForEach(section.items, id: \.name) { credit in
                        CreditRow(credit: credit)
                    }
                }
            }
        }
        .navigationTitle(L10n.t("credits_title"))
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct CreditRow: View {
    let credit: Credit

    var body: some View {
        let link: URL? = URL(string: credit.url)
        VStack(alignment: .leading, spacing: 2) {
            if let link {
                Link(credit.name, destination: link).font(.headline)
            } else {
                Text(credit.name).font(.headline)
            }
            Text(credit.use).font(.caption)
            Text(credit.license).font(.caption2).foregroundStyle(.secondary)
        }
    }
}
