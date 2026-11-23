package app.guitarnest.listen

import app.guitarnest.ai.SongInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Song identification with AcoustID (https://acoustid.org), only when the player turns it on.
 * Sent: the Chromaprint fingerprint and the length of the audio. Never the audio itself.
 * Song data comes from MusicBrainz through AcoustID (AcoustID data: CC BY-SA 3.0).
 */
object AcoustId {
    /** Application key from https://acoustid.org/new-application. Empty: the feature is hidden. */
    const val CLIENT_KEY = ""
    const val URL = "https://api.acoustid.org/v2/lookup"
    /** Results below this score are not shown. */
    const val MIN_SCORE = 0.5

    val available: Boolean get() = CLIENT_KEY.isNotBlank()

    /** Fingerprint of the first [Chromaprint.MAX_SECONDS] of [audio]. Null when the audio is too short. */
    fun fingerprint(audio: FloatArray, sampleRate: Int): Pair<String, Int>? {
        val seconds = audio.size / sampleRate
        if (seconds < Chromaprint.MIN_SECONDS) return null
        val part = audio.copyOf(minOf(audio.size, Chromaprint.MAX_SECONDS * sampleRate))
        val mono = if (sampleRate == Chromaprint.SAMPLE_RATE) part else Resample.toRate(part, sampleRate, Chromaprint.SAMPLE_RATE)
        return Chromaprint.encode(mono) to seconds
    }

    /** Form body for a POST to [URL]. */
    fun form(fingerprint: String, seconds: Int, key: String = CLIENT_KEY): String =
        "client=$key&format=json&meta=recordings+releasegroups&duration=$seconds&fingerprint=$fingerprint"

    /** Matches, best first. Throws [IllegalStateException] with AcoustID's message on an error answer. */
    fun parse(body: String): List<SongInfo> {
        val answer = json.decodeFromString(Answer.serializer(), body)
        if (answer.status != "ok") error(answer.error?.message ?: "AcoustID error")
        return answer.results.filter { it.score >= MIN_SCORE }.sortedByDescending { it.score }.flatMap { result ->
            result.recordings.filter { it.title.isNotBlank() }.map { r ->
                SongInfo(
                    title = r.title,
                    artist = r.artists.joinToString("") { it.name + (it.joinphrase ?: "") }.trim(),
                    album = r.releasegroups.firstOrNull { it.type == "Album" }?.title ?: r.releasegroups.firstOrNull()?.title ?: "",
                    year = "",
                    seconds = r.duration?.toInt(),
                )
            }
        }.distinctBy { it.title.lowercase() to it.artist.lowercase() }
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable private data class Answer(val status: String = "", val results: List<Result> = emptyList(), val error: Problem? = null)
    @Serializable private data class Problem(val message: String = "")
    @Serializable private data class Result(val score: Double = 0.0, val recordings: List<Recording> = emptyList())
    @Serializable private data class Recording(
        val title: String = "",
        val duration: Double? = null,
        val artists: List<Artist> = emptyList(),
        val releasegroups: List<ReleaseGroup> = emptyList(),
    )
    @Serializable private data class Artist(val name: String = "", val joinphrase: String? = null)
    @Serializable private data class ReleaseGroup(val title: String = "", val type: String? = null)
}
