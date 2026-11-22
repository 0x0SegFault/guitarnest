import Foundation

/// Texts come from the shared translations (generated from the Android strings by scripts/ios_strings.py).
enum L10n {
    static func t(_ key: String) -> String {
        NSLocalizedString(key, comment: "")
    }

    static func t(_ key: String, _ args: CVarArg...) -> String {
        String(format: NSLocalizedString(key, comment: ""), locale: Locale.current, arguments: args)
    }

    /// Days since 1970-01-01 in the phone's time zone (the practice log uses these).
    static func today() -> Int64 {
        let parts = Calendar.current.dateComponents([.year, .month, .day], from: Date())
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(identifier: "UTC") ?? .current
        let date = utc.date(from: parts) ?? Date()
        return Int64(floor(date.timeIntervalSince1970 / 86_400))
    }
}
