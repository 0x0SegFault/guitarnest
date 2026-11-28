package app.guitarnest.scores

import app.guitarnest.tab.MidiImport
import app.guitarnest.tab.Song

/** A free guitar score: public domain or Creative Commons. */
data class FreeScore(
    val title: String,
    val composer: String,
    val opus: String,
    val date: String,
    val style: String,
    val licence: String,
    /** Path of the MIDI file on the Mutopia Project site. */
    val midiPath: String,
    val mutopiaId: String,
) {
    val midiUrl: String get() = FreeScores.SITE + midiPath
    /** The piece's page, which shows the score and its licence. */
    val pageUrl: String get() = if (mutopiaId.isNotEmpty()) "${FreeScores.SITE}cgibin/piece-info.cgi?id=$mutopiaId" else FreeScores.SITE
}

/**
 * Free guitar scores from the Mutopia Project, searched on the phone (index made by scripts/mutopia_index.py).
 * Nothing is sent while searching. The MIDI file is downloaded only when the player taps a score.
 */
object FreeScores {
    const val SITE = "https://www.mutopiaproject.org/"

    val all: List<FreeScore> by lazy {
        MutopiaData.CHUNKS.flatMap { it.split('\n') }.mapNotNull { row ->
            val f = row.split('\t')
            if (f.size < 8) null else FreeScore(f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7])
        }
    }

    /** Scores whose title, composer or opus contain every word of [query], accents and case ignored. */
    fun search(query: String, limit: Int = 30): List<FreeScore> {
        val words = fold(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return all.filter { s ->
            val text = fold("${s.title} ${s.composer} ${s.opus}")
            words.all { it in text }
        }.take(limit)
    }

    /** Where the song came from, kept with it: composer, the Mutopia Project and the licence. */
    fun source(score: FreeScore): String =
        listOf(score.composer, "Mutopia Project", score.licence).filter { it.isNotBlank() }.joinToString(" · ")

    /** The downloaded MIDI file as a song. Its notes keep the licence and the address of the score. */
    fun toSong(score: FreeScore, midi: ByteArray): Song {
        val song = MidiImport.import(midi, score.title, source(score))
        return song.copy(
            title = score.title,
            artist = score.composer,
            notes = listOfNotNull(score.opus.ifBlank { null }, score.date.ifBlank { null }, "${score.licence}: ${score.pageUrl}").joinToString(" · "),
        )
    }

    private fun fold(text: String): String = buildString {
        for (c in text.lowercase()) append(ACCENTS[c] ?: if (c.isLetterOrDigit()) c else ' ')
    }

    private val ACCENTS: Map<Char, Char> = buildMap {
        "àáâãäå" .forEach { put(it, 'a') }
        "èéêë".forEach { put(it, 'e') }
        "ìíîï".forEach { put(it, 'i') }
        "òóôõöø".forEach { put(it, 'o') }
        "ùúûü".forEach { put(it, 'u') }
        put('ç', 'c'); put('ñ', 'n'); put('ý', 'y'); put('ÿ', 'y')
    }
}
