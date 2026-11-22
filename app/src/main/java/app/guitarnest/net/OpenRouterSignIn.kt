package app.guitarnest.net

import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * "Sign in with OpenRouter": OAuth PKCE (https://openrouter.ai/docs/use-cases/oauth-pkce).
 * The browser returns to a one-time server on this phone (http://localhost:<port>), so no website
 * is involved. The result is an API key the player can revoke at openrouter.ai.
 */
object OpenRouterSignIn {
    const val BASE_URL = "https://openrouter.ai/api/v1"
    /** OpenRouter picks a suitable model; the player can change it. */
    const val DEFAULT_MODEL = "openrouter/auto"

    suspend fun signIn(context: Context): String = withContext(Dispatchers.IO) {
        val verifier = base64Url(ByteArray(32).also { SecureRandom().nextBytes(it) })
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 5 * 60_000
            val callback = "http://localhost:${server.localPort}/callback"
            val url = "https://openrouter.ai/auth?callback_url=${enc(callback)}&code_challenge=$challenge" +
                "&code_challenge_method=S256&key_label=GuitarNest"
            withContext(Dispatchers.Main) {
                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            val code = try {
                waitForCode(server)
            } catch (_: SocketTimeoutException) {
                throw AiException("Sign-in timed out.")
            }
            exchange(code, verifier)
        }
    }

    /** Reads the browser's request to /callback?code=..., answers with a page that leads back to the app. */
    private fun waitForCode(server: ServerSocket): String {
        while (true) {
            server.accept().use { socket ->
                val line = socket.getInputStream().bufferedReader().readLine() ?: return@use
                val target = line.split(" ").getOrNull(1) ?: return@use
                if (!target.startsWith("/callback")) {
                    socket.getOutputStream().write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                    return@use
                }
                val code = target.substringAfter("?", "").split("&").map { it.split("=", limit = 2) }
                    .firstOrNull { it[0] == "code" }?.getOrNull(1)?.let { URLDecoder.decode(it, "UTF-8") }
                val page = PAGE.toByteArray()
                socket.getOutputStream().write(
                    ("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${page.size}\r\nConnection: close\r\n\r\n").toByteArray() + page,
                )
                return code ?: throw AiException("OpenRouter did not send a code.")
            }
        }
    }

    private fun exchange(code: String, verifier: String): String {
        val http = (URL("https://openrouter.ai/api/v1/auth/keys").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            val body = """{"code":${Json.encodeToString(code)},"code_verifier":"$verifier","code_challenge_method":"S256"}"""
            http.outputStream.use { it.write(body.toByteArray()) }
            if (http.responseCode !in 200..299) throw AiException("OpenRouter refused the sign-in (${http.responseCode}).")
            val text = http.inputStream.use { it.readBytes().decodeToString() }
            return Json.parseToJsonElement(text).jsonObject["key"]?.jsonPrimitive?.content ?: throw AiException("OpenRouter sent no key.")
        } catch (e: IOException) {
            throw AiException("Cannot reach OpenRouter: ${e.message}")
        } finally {
            http.disconnect()
        }
    }

    private fun base64Url(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private const val PAGE = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<title>GuitarNest</title></head><body style="font-family:sans-serif;text-align:center;padding:48px 16px">
<h2>Signed in</h2><p>You can go back to GuitarNest.</p>
<p><a href="guitarnest://signed-in" style="font-size:20px">Open GuitarNest</a></p></body></html>"""
}
