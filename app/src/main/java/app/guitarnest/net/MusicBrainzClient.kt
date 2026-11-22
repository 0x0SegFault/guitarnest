package app.guitarnest.net

import app.guitarnest.ai.SongInfo
import app.guitarnest.ai.SongSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object MusicBrainzClient {
    suspend fun search(query: String): List<SongInfo> = withContext(Dispatchers.IO) {
        val http = (URL(SongSearch.url(query)).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", SongSearch.USER_AGENT)
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (http.responseCode != 200) throw AiException("MusicBrainz answered ${http.responseCode}. Try again in a moment.")
            SongSearch.parse(http.inputStream.use { it.readBytes().decodeToString() })
        } catch (e: IOException) {
            throw AiException("Cannot reach MusicBrainz: ${e.message}")
        } finally {
            http.disconnect()
        }
    }
}
