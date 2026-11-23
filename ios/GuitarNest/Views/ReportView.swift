import GuitarNestShared
import SwiftUI
import UIKit

/// Report a problem or an idea: anonymously to GuitarNest's report service (once set up), on GitHub, or as text.
struct ReportView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var model: AppModel
    @State private var kind = "bug"
    @State private var title = ""
    @State private var details = ""
    @State private var withDevice = true
    @State private var sending = false
    @State private var error: String?
    @State private var sharing: ExportFile?
    @State private var shareText: String?

    private var version: String { Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "" }
    private var device: String { withDevice ? "\(UIDevice.current.model), iOS \(UIDevice.current.systemVersion)" : "" }
    private var valid: Bool { title.trimmingCharacters(in: .whitespaces).count >= 5 && details.trimmingCharacters(in: .whitespaces).count >= 10 }
    private var serviceUrl: String? { Bridge.shared.reportServiceUrl() }

    var body: some View {
        Form {
            Picker("", selection: $kind) {
                Text(L10n.t("report_problem")).tag("bug")
                Text(L10n.t("report_idea")).tag("feature")
            }
            .pickerStyle(.segmented)
            TextField(L10n.t("report_short"), text: $title)
            TextField(L10n.t("report_details"), text: $details, axis: .vertical).lineLimit(4...10)
            Toggle(L10n.t("report_with_device", "GuitarNest \(version) · \(UIDevice.current.model), iOS \(UIDevice.current.systemVersion)"), isOn: $withDevice)
                .font(.caption)
            Text(L10n.t(serviceUrl == nil ? "report_not_ready" : "report_privacy")).font(.caption).foregroundStyle(.secondary)
            if let error { Text(error).foregroundStyle(Palette.off) }
            if serviceUrl != nil {
                Button(L10n.t(sending ? "working" : "report_send")) { send() }.disabled(!valid || sending)
            }
            Button(L10n.t("report_github")) {
                if let url = URL(string: Bridge.shared.reportGithubUrl(kind: kind, title: title, details: details, version: version, device: device)) {
                    UIApplication.shared.open(url)
                }
                dismiss()
            }
            .disabled(!valid)
            Button(L10n.t("report_share")) {
                shareText = Bridge.shared.reportShareText(kind: kind, title: title, details: details, version: version, device: device)
            }
            .disabled(!valid)
        }
        .navigationTitle(L10n.t("report_title"))
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: Binding(get: { shareText.map { SharedText(text: $0) } }, set: { shareText = $0?.text })) { item in ShareSheet(items: [item.text]) }
    }

    private struct SharedText: Identifiable {
        let text: String
        var id: String { text }
    }

    private func send() {
        guard let base = serviceUrl else { return }
        sending = true
        error = nil
        let body = (kind, title, details, version, device)
        Task {
            do {
                let (challengeData, _) = try await URLSession.shared.data(from: URL(string: base + "/challenge")!)
                let challenge = try Bridge.shared.reportChallenge(body: String(bytes: challengeData, encoding: .utf8) ?? "")
                let proof = await Task.detached { Bridge.shared.reportSolve(challenge: challenge) }.value
                guard let proof else { throw AppError("Could not solve the anti-spam puzzle.") }
                var request = URLRequest(url: URL(string: base + "/report")!)
                request.httpMethod = "POST"
                request.setValue("application/json", forHTTPHeaderField: "Content-Type")
                request.httpBody = Data(Bridge.shared.reportBody(kind: body.0, title: body.1, details: body.2, version: body.3, device: body.4, proof: proof).utf8)
                let (_, response) = try await URLSession.shared.data(for: request)
                guard (response as? HTTPURLResponse)?.statusCode == 200 else { throw AppError("The report service did not accept the report.") }
                model.message = L10n.t("report_sent")
                dismiss()
            } catch {
                self.error = error.localizedDescription
            }
            sending = false
        }
    }
}
