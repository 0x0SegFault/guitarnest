import SwiftUI

struct AboutView: View {
    var body: some View {
        Form {
            Section {
                Text(L10n.t("about_privacy"))
            }
            Section(L10n.t("permissions")) {
                Text(L10n.t("permission_mic"))
                Text(L10n.t("permission_none_else"))
            }
            Section {
                Text(L10n.t("copyright_note")).font(.caption)
                Text(L10n.t("version", Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "")).font(.caption)
                NavigationLink(L10n.t("credits_title")) { CreditsView() }
                if let url = URL(string: "https://github.com/0x0SegFault/guitarnest/blob/main/docs/OPEN_MUSIC.md") { Link(L10n.t("open_music"), destination: url) }
                if let url = URL(string: "https://github.com/0x0SegFault/guitarnest") { Link(L10n.t("source_code"), destination: url) }
            }
        }
        .navigationTitle(L10n.t("about"))
        .navigationBarTitleDisplayMode(.inline)
    }
}
