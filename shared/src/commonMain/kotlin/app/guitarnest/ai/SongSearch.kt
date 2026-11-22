package app.guitarnest.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A search result: song details only, never a tab. */
data class SongInfo(val title: String, val artist: String, val album: String, val year: String, val seconds: Int?)

/**
 * Song search in MusicBrainz (open data, CC0: https://musicbrainz.org/doc/About/Data_License).
 * Sent only when the player taps Search: the search words and the app name, nothing else.
 */
object SongSearch {
    const val USER_AGENT = "GuitarNest/0.1 ( https://github.com/0x0SegFault/guitarnest )"
    private val json = Json { ignoreUnknownKeys = true }

    fun url(query: String): String =
        "https://musicbrainz.org/ws/2/recording?fmt=json&limit=15&query=" + encode(query.trim())

    fun parse(body: String): List<SongInfo> {
        val result = json.decodeFromString(Result.serializer(), body)
        return result.recordings.map { r ->
            val release = r.releases.minByOrNull { it.date ?: "9999" }
            SongInfo(
                title = r.title,
                artist = r.artistCredit.joinToString("") { it.name + (it.joinphrase ?: "") }.trim(),
                album = release?.title ?: "",
                year = release?.date?.take(4) ?: "",
                seconds = r.length?.let { it / 1000 },
            )
        }.distinctBy { Triple(it.title.lowercase(), it.artist.lowercase(), it.year) }
    }

    /** Percent-encoding for a query string (RFC 3986 unreserved characters kept). */
    private fun encode(text: String): String = buildString {
        for (b in text.encodeToByteArray()) {
            val c = b.toInt() and 0xFF
            if (c.toChar().isLetterOrDigit() && c < 128 || c.toChar() in "-_.~") append(c.toChar())
            else append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 15])
        }
    }

    @Serializable private data class Result(val recordings: List<Recording> = emptyList())
    @Serializable private data class Recording(
        val title: String = "",
        val length: Int? = null,
        @SerialName("artist-credit") val artistCredit: List<Credit> = emptyList(),
        val releases: List<Release> = emptyList(),
    )
    @Serializable private data class Credit(val name: String = "", val joinphrase: String? = null)
    @Serializable private data class Release(val title: String = "", val date: String? = null)
}
