package app.guitarnest.net

import app.guitarnest.ai.SongSearch
import app.guitarnest.scores.FreeScore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Downloads one free score (a MIDI file) from the Mutopia Project, only when the player taps it. */
object ScoreClient {
    private const val MAX_BYTES = 2_000_000

    suspend fun midi(score: FreeScore): ByteArray = withContext(Dispatchers.IO) {
        val http = (URL(score.midiUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", SongSearch.USER_AGENT)
        }
        try {
            if (http.responseCode != 200) throw AiException("The Mutopia Project answered ${http.responseCode}.")
            http.inputStream.use { input ->
                val bytes = input.readNBytesCompat(MAX_BYTES + 1)
                if (bytes.size > MAX_BYTES) throw AiException("This score is too large.")
                bytes
            }
        } catch (e: IOException) {
            throw AiException("Cannot reach the Mutopia Project: ${e.message}")
        } finally {
            http.disconnect()
        }
    }

    /** InputStream.readNBytes needs Android 13; this works on every version. */
    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16_384)
        while (out.size() < max) {
            val n = read(buf, 0, minOf(buf.size, max - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
