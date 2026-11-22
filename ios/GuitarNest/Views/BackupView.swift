import SwiftUI
import UniformTypeIdentifiers

/// One encrypted file with all songs and the practice history (same format as Android).
struct BackupView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var password = ""
    @State private var repeatPassword = ""
    @State private var document: BackupDocument?
    @State private var exporting = false
    @State private var sharing: ExportFile?
    @State private var importing = false
    @State private var restoreData: Data?
    @State private var restorePassword = ""
    @State private var error: String?
    @State private var working = false

    private var valid: Bool { password.count >= 8 && password == repeatPassword }
    private var fileName: String { "guitarnest-\(Date().formatted(.iso8601.year().month().day())).gnbackup" }

    var body: some View {
        Form {
            Section {
                Text(L10n.t("backup_help")).font(.callout)
                SecureField(L10n.t("backup_password"), text: $password)
                SecureField(L10n.t("backup_repeat"), text: $repeatPassword)
                Text(L10n.t("backup_password_hint")).font(.caption).foregroundStyle(.secondary)
                Button(L10n.t("backup_to_file")) { make { document = BackupDocument(data: $0); exporting = true } }.disabled(!valid || working)
                Button(L10n.t("backup_to_app")) {
                    make { data in
                        let url = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)
                        try? data.write(to: url)
                        sharing = ExportFile(url: url)
                    }
                }
                .disabled(!valid || working)
            }
            Section(L10n.t("restore_title")) {
                Text(L10n.t("restore_help")).font(.caption).foregroundStyle(.secondary)
                if restoreData == nil {
                    Button(L10n.t("restore_choose")) { importing = true }
                } else {
                    SecureField(L10n.t("backup_password"), text: $restorePassword)
                    Button(L10n.t("restore_now")) { restore() }.disabled(restorePassword.isEmpty || working)
                }
                if let error { Text(error).foregroundStyle(Palette.off) }
            }
            if working { ProgressView(L10n.t("working")) }
        }
        .navigationTitle(L10n.t("backup_title"))
        .navigationBarTitleDisplayMode(.inline)
        .fileExporter(isPresented: $exporting, document: document, contentType: .data, defaultFilename: fileName) { result in
            if case .success = result { model.message = L10n.t("backup_saved"); dismiss() }
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.data, .item]) { result in
            guard case .success(let url) = result else { return }
            let access = url.startAccessingSecurityScopedResource()
            defer { if access { url.stopAccessingSecurityScopedResource() } }
            restoreData = try? Data(contentsOf: url)
            if restoreData == nil { error = L10n.t("import_failed") }
        }
        .sheet(item: $sharing) { file in ShareSheet(items: [file.url]) }
    }

    private func make(_ then: @escaping (Data) -> Void) {
        working = true
        let text = model.backupText()
        let pass = password
        Task.detached(priority: .userInitiated) {
            let data = try? BackupCrypto.encrypt(Data(text.utf8), password: pass)
            await MainActor.run {
                working = false
                if let data { then(data) }
            }
        }
    }

    private func restore() {
        guard let blob = restoreData else { return }
        working = true
        error = nil
        let pass = restorePassword
        Task.detached(priority: .userInitiated) {
            let result: Result<Data, Error> = Result { try BackupCrypto.decrypt(blob, password: pass) }
            await MainActor.run {
                working = false
                switch result {
                case .success(let plain):
                    do {
                        let count = try model.restore(String(bytes: plain, encoding: .utf8) ?? "")
                        model.message = String.localizedStringWithFormat(NSLocalizedString("restored_songs", comment: ""), count)
                        dismiss()
                    } catch {
                        self.error = L10n.t("backup_not_a_backup")
                    }
                case .failure(let failure):
                    self.error = L10n.t((failure as? BackupCrypto.Failure) == .wrongPassword ? "backup_wrong_password" : "backup_not_a_backup")
                }
            }
        }
    }
}

struct BackupDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.data] }
    let data: Data
    init(data: Data) { self.data = data }
    init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}
