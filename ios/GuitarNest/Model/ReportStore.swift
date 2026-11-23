import Foundation
import GuitarNestShared

/// Reports sent from this iPhone (title, date, follow-up code), for "My reports".
enum ReportStore {
    private static var file: URL {
        let base = (try? FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true))
            ?? FileManager.default.temporaryDirectory
        return base.appendingPathComponent("sent_reports.json")
    }

    static func sent() -> [Reports.Sent] {
        guard let text = try? String(contentsOf: file, encoding: .utf8) else { return [] }
        return Bridge.shared.reportDecodeSent(text: text)
    }

    static func add(code: String, kind: String, title: String) {
        let entry = Bridge.shared.reportSent(code: code, kind: kind, title: title, day: L10n.today())
        try? Bridge.shared.reportEncodeSent(list: [entry] + sent()).write(to: file, atomically: true, encoding: .utf8)
    }

    /// 32 random hex characters: the follow-up code that only this phone knows.
    static func newCode() -> String {
        var bytes = [UInt8](repeating: 0, count: 16)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        return bytes.map { String(format: "%02x", $0) }.joined()
    }

    static func status(_ code: String) async -> Reports.Status {
        guard let base = Bridge.shared.reportServiceUrl(), let url = URL(string: base + "/status?code=" + code) else {
            return Bridge.shared.reportParseStatus(body: "")
        }
        guard let (data, _) = try? await URLSession.shared.data(from: url) else { return Bridge.shared.reportParseStatus(body: "{\"status\":\"offline\"}") }
        return Bridge.shared.reportParseStatus(body: String(bytes: data, encoding: .utf8) ?? "")
    }
}
