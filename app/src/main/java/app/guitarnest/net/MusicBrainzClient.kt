package app.guitarnest.net

import app.guitarnest.ai.SongInfo
import app.guitarnest.ai.SongSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * MusicBrainz allows about 1 request per second per IP address and answers 503 above that
 * (https://musicbrainz.org/doc/MusicBrainz_API/Rate_Limiting). Shared networks hit this often,
 * so a 503 waits for Retry-After and tries again, up to 3 times.
 */
object MusicBrainzClient {
    suspend fun search(query: String): List<SongInfo> = withContext(Dispatchers.IO) {
        for (attempt in 1..ATTEMPTS) {
            val http = (URL(SongSearch.url(query)).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", SongSearch.USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }
            try {
                val code = http.responseCode
                if (code == 200) return@withContext SongSearch.parse(http.inputStream.use { it.readBytes().decodeToString() })
                if (code != 503) throw AiException("MusicBrainz answered $code.")
                if (attempt < ATTEMPTS) {
                    val wait = http.getHeaderField("Retry-After")?.toLongOrNull()?.coerceIn(1, 10) ?: attempt.toLong()
                    delay(wait * 1000 + 200)
                }
            } catch (e: IOException) {
                throw AiException("Cannot reach MusicBrainz: ${e.message}")
            } finally {
                http.disconnect()
            }
        }
        throw AiException("MusicBrainz is busy. Try again in a minute.")
    }

    private const val ATTEMPTS = 3
}
