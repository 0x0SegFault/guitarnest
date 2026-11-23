package app.guitarnest.net

import app.guitarnest.ai.SongInfo
import app.guitarnest.listen.AcoustId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Sends a fingerprint to AcoustID. See [AcoustId] for what is sent. */
object AcoustIdClient {
    suspend fun identify(fingerprint: String, seconds: Int): List<SongInfo> = withContext(Dispatchers.IO) {
        val http = (URL(AcoustId.URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("User-Agent", app.guitarnest.ai.SongSearch.USER_AGENT)
        }
        try {
            http.outputStream.use { it.write(AcoustId.form(fingerprint, seconds).encodeToByteArray()) }
            val stream = if (http.responseCode in 200..299) http.inputStream else http.errorStream ?: throw AiException("AcoustID answered ${http.responseCode}.")
            runCatching { AcoustId.parse(stream.use { it.readBytes().decodeToString() }) }
                .getOrElse { throw AiException("AcoustID: ${it.message}") }
        } catch (e: IOException) {
            throw AiException("Cannot reach AcoustID: ${e.message}")
        } finally {
            http.disconnect()
        }
    }
}
