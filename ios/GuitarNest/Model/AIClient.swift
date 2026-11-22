import Foundation
import GuitarNestShared

/// The player's own AI: Anthropic's API for api.anthropic.com, else any OpenAI-compatible endpoint.
struct AIConnection: Equatable {
    var baseUrl: String
    var model: String
    var apiKey: String

    var configured: Bool { !baseUrl.isEmpty && !model.isEmpty }
    var unencrypted: Bool { baseUrl.lowercased().hasPrefix("http://") }

    static func load() -> AIConnection {
        let defaults = UserDefaults.standard
        return AIConnection(baseUrl: defaults.string(forKey: "ai.url") ?? "", model: defaults.string(forKey: "ai.model") ?? "", apiKey: Keychain.read())
    }

    func save() {
        let url = baseUrl.trimmingCharacters(in: .whitespaces)
        UserDefaults.standard.set(url.hasSuffix("/") ? String(url.dropLast()) : url, forKey: "ai.url")
        UserDefaults.standard.set(model.trimmingCharacters(in: .whitespaces), forKey: "ai.model")
        Keychain.write(apiKey.trimmingCharacters(in: .whitespaces))
    }

    static func clear() {
        UserDefaults.standard.removeObject(forKey: "ai.url")
        UserDefaults.standard.removeObject(forKey: "ai.model")
        Keychain.write("")
    }
}

enum AIClient {
    static func chat(_ connection: AIConnection, _ request: ChatRequest) async throws -> String {
        let base = connection.baseUrl.hasSuffix("/") ? String(connection.baseUrl.dropLast()) : connection.baseUrl
        guard let url = URL(string: base + Bridge.shared.aiPath(baseUrl: base)), ["http", "https"].contains(url.scheme ?? "") else {
            throw AppError(L10n.t("ai_address"))
        }
        var http = URLRequest(url: url, timeoutInterval: 180)
        http.httpMethod = "POST"
        http.setValue("application/json", forHTTPHeaderField: "Content-Type")
        http.setValue("GuitarNest", forHTTPHeaderField: "User-Agent")
        if Bridge.shared.aiIsAnthropic(baseUrl: base) {
            http.setValue(connection.apiKey, forHTTPHeaderField: "x-api-key")
            http.setValue("2023-06-01", forHTTPHeaderField: "anthropic-version")
        } else if !connection.apiKey.isEmpty {
            http.setValue("Bearer \(connection.apiKey)", forHTTPHeaderField: "Authorization")
        }
        http.httpBody = Data(Bridge.shared.aiBody(request: request, baseUrl: base).utf8)
        let (data, response) = try await URLSession.shared.data(for: http)
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        let body = String(data: data, encoding: .utf8) ?? ""
        guard (200..<300).contains(code) else {
            throw AppError("The AI server answered \(code): \(body.prefix(200))")
        }
        return try Bridge.shared.aiAnswer(body: body, baseUrl: base)
    }

    /// A song from the AI; an invalid answer goes back once with the problem.
    static func makeSong(_ connection: AIConnection, taskIndex: Int, level: Level, request: String, song: Song?) async throws -> Song {
        var chat = Bridge.shared.aiRequest(model: connection.model, task: Bridge.shared.aiTask(index: Int32(taskIndex)), level: level, request: request, song: song)
        var answer = try await AIClient.chat(connection, chat)
        let keep: Level? = taskIndex == 1 ? nil : level // 1 = strumming: keep the song's level
        do {
            return try Bridge.shared.aiSong(answer: answer, source: "AI (\(connection.model))", level: keep)
        } catch {
            chat = Bridge.shared.aiRepair(request: chat, answer: answer, problem: error.localizedDescription)
            answer = try await AIClient.chat(connection, chat)
            return try Bridge.shared.aiSong(answer: answer, source: "AI (\(connection.model))", level: keep)
        }
    }

    /// Song search in MusicBrainz (open data). Only the search words are sent. MusicBrainz allows about
    /// 1 request per second per IP and answers 503 above that: wait for Retry-After and try again, up to 3 times.
    static func search(_ query: String) async throws -> [SongInfo] {
        guard let url = URL(string: Bridge.shared.searchUrl(query: query)) else { return [] }
        var http = URLRequest(url: url, timeoutInterval: 20)
        http.setValue(Bridge.shared.searchUserAgent(), forHTTPHeaderField: "User-Agent")
        http.setValue("application/json", forHTTPHeaderField: "Accept")
        for attempt in 1...3 {
            let (data, response) = try await URLSession.shared.data(for: http)
            let status = (response as? HTTPURLResponse)?.statusCode ?? 0
            if status == 200 { return try Bridge.shared.searchParse(body: String(bytes: data, encoding: .utf8) ?? "") }
            guard status == 503, attempt < 3 else { break }
            let wait = Double((response as? HTTPURLResponse)?.value(forHTTPHeaderField: "Retry-After") ?? "") ?? Double(attempt)
            try await Task.sleep(nanoseconds: UInt64((min(10, max(1, wait)) + 0.2) * 1_000_000_000))
        }
        throw AppError("MusicBrainz is busy. Try again in a minute.")
    }
}
