import AuthenticationServices
import CryptoKit
import Foundation
import Network
import UIKit

/// "Sign in with OpenRouter": OAuth PKCE (https://openrouter.ai/docs/use-cases/oauth-pkce).
/// The sign-in page opens inside the app; OpenRouter returns to a one-time listener on this iPhone
/// (http://localhost:<port>), so no website is involved. The result is a key the player can revoke.
@MainActor
final class OpenRouterSignIn: NSObject, ASWebAuthenticationPresentationContextProviding {
    static let baseUrl = "https://openrouter.ai/api/v1"
    static let defaultModel = "openrouter/auto"

    private var listener: NWListener?
    private var session: ASWebAuthenticationSession?

    func signIn() async throws -> String {
        let verifier = Self.base64Url(Data((0..<32).map { _ in UInt8.random(in: 0...255) }))
        let challenge = Self.base64Url(Data(SHA256.hash(data: Data(verifier.utf8))))
        let code: String = try await withCheckedThrowingContinuation { continuation in
            var finished = false
            func finish(_ result: Result<String, Error>) {
                guard !finished else { return }
                finished = true
                listener?.cancel()
                session?.cancel()
                continuation.resume(with: result)
            }
            do {
                let listener = try NWListener(using: .tcp, on: .any)
                self.listener = listener
                listener.newConnectionHandler = { connection in
                    connection.start(queue: .main)
                    connection.receive(minimumIncompleteLength: 1, maximumLength: 8192) { data, _, _, _ in
                        let request = data.flatMap { String(data: $0, encoding: .utf8) } ?? ""
                        let target = request.split(separator: " ").dropFirst().first.map(String.init) ?? ""
                        let code = URLComponents(string: "http://localhost" + target)?.queryItems?.first { $0.name == "code" }?.value
                        let page = "<html><body style='font-family:sans-serif;text-align:center;padding:48px'><h2>Signed in</h2></body></html>"
                        let reply = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: \(page.utf8.count)\r\nConnection: close\r\n\r\n" + page
                        connection.send(content: Data(reply.utf8), completion: .contentProcessed { _ in connection.cancel() })
                        Task { @MainActor in
                            if let code { finish(.success(code)) }
                        }
                    }
                }
                listener.stateUpdateHandler = { state in
                    guard case .ready = state, let port = listener.port?.rawValue else { return }
                    Task { @MainActor in
                        var parts = URLComponents(string: "https://openrouter.ai/auth")!
                        parts.queryItems = [
                            URLQueryItem(name: "callback_url", value: "http://localhost:\(port)/callback"),
                            URLQueryItem(name: "code_challenge", value: challenge),
                            URLQueryItem(name: "code_challenge_method", value: "S256"),
                            URLQueryItem(name: "key_label", value: "GuitarNest")
                        ]
                        let session = ASWebAuthenticationSession(url: parts.url!, callbackURLScheme: "guitarnest") { _, error in
                            // The listener normally finishes first; this only reports a cancel by the player.
                            if let error { Task { @MainActor in finish(.failure(error)) } }
                        }
                        session.presentationContextProvider = self
                        session.prefersEphemeralWebBrowserSession = false
                        self.session = session
                        session.start()
                    }
                }
                listener.start(queue: .main)
            } catch {
                finish(.failure(error))
            }
        }
        return try await Self.exchange(code: code, verifier: verifier)
    }

    nonisolated func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        MainActor.assumeIsolated {
            UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.keyWindow }.first ?? ASPresentationAnchor()
        }
    }

    private static func exchange(code: String, verifier: String) async throws -> String {
        var request = URLRequest(url: URL(string: "https://openrouter.ai/api/v1/auth/keys")!, timeoutInterval: 30)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: ["code": code, "code_verifier": verifier, "code_challenge_method": "S256"])
        let (data, response) = try await URLSession.shared.data(for: request)
        guard (response as? HTTPURLResponse)?.statusCode == 200,
              let json = try JSONSerialization.jsonObject(with: data) as? [String: Any], let key = json["key"] as? String else {
            throw AppError("OpenRouter refused the sign-in.")
        }
        return key
    }

    private static func base64Url(_ data: Data) -> String {
        data.base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
    }
}
