package app.guitarnest.songbook

import app.guitarnest.listen.Fingering
import app.guitarnest.listen.HeardNote
import app.guitarnest.tab.Bar
import app.guitarnest.tab.Beat
import app.guitarnest.tab.ChordMark
import app.guitarnest.tab.Level
import app.guitarnest.tuner.Notes
import app.guitarnest.tab.Section
import app.guitarnest.tab.Song
import app.guitarnest.tab.StrumPattern
import app.guitarnest.tab.TabFormatException
import app.guitarnest.tab.TabNote
import app.guitarnest.tuner.Tuning

/**
 * A song of the song book: a public-domain melody with our own chords.
 *
 * [melody]: notes with their length in beats, for example `"E4:1 D4:0.5 C4:0.5 r:2"` (`r` is a rest).
 * [chords]: one token per beat, `"."` keeps the chord. `"|"` between bars, in both lines, is only for reading.
 * The first bar may start with rests for a pickup.
 */
data class BookSong(
    val id: String,
    val title: String,
    /** Composer or "Traditional", with the country and year when known. Shown as the artist. */
    val origin: String,
    /** Why the melody is in the public domain, for the record. */
    val publicDomain: String,
    val tempo: Int,
    val beatsPerBar: Int,
    val melody: String,
    val chords: String,
)

/**
 * The song book: public-domain melodies arranged for GuitarNest, at three levels. No lyrics.
 * The arrangements are part of GuitarNest and use its licence (EUPL-1.2).
 */
object SongBook {
    const val SOURCE = "GuitarNest song book"

    fun songs(): List<BookSong> = SongBookData.SONGS

    /** The song at a level. Beginner: chords and a simple strum. Intermediate: melody and chords. Advanced: melody and bass, fingerstyle. */
    fun song(book: BookSong, level: Level): Song {
        val melody = parseMelody(book.melody, book.beatsPerBar)
        val chords = parseChords(book.chords, book.beatsPerBar)
        if (chords.size != melody.size) throw TabFormatException("${book.id}: ${melody.size} melody bars, ${chords.size} chord bars")
        val tuning = Tuning.PRESETS.first { it.id == "standard" }.strings
        val bars = melody.indices.map { b ->
            val marks = chords[b]
            val notes: List<Beat> = when (level) {
                Level.BEGINNER -> emptyList()
                Level.INTERMEDIATE -> place(melody[b], tuning)
                Level.ADVANCED -> place(melody[b] + bass(marks, book.beatsPerBar), tuning)
            }
            Bar(chords = marks, beats = notes)
        }
        val pattern = when (level) {
            Level.BEGINNER -> StrumPattern(id = "simple", name = "Simple", steps = "D-".repeat(book.beatsPerBar), perBeat = 2)
            Level.INTERMEDIATE -> StrumPattern(id = "folk", name = "Folk", steps = if (book.beatsPerBar == 3) "D-DUDU" else "D-DU-UDU".take(book.beatsPerBar * 2).padEnd(book.beatsPerBar * 2, '-'), perBeat = 2)
            Level.ADVANCED -> null
        }
        return Song(
            title = book.title,
            artist = book.origin,
            source = SOURCE,
            level = level,
            tempo = book.tempo,
            beatsPerBar = book.beatsPerBar,
            patterns = listOfNotNull(pattern),
            sections = listOf(Section(name = "Song", bars = bars, strum = pattern?.id)),
            notes = "Melody: public domain (${book.publicDomain}). Arrangement: GuitarNest song book, EUPL-1.2.",
        )
    }

    /** A timed note: [midi] null is a rest. */
    data class Timed(val at: Double, val midi: Int?, val length: Double)

    /** The melody split into bars of [beatsPerBar] beats. */
    fun parseMelody(text: String, beatsPerBar: Int): List<List<Timed>> {
        val bars = mutableListOf<MutableList<Timed>>(mutableListOf())
        var pos = 0.0
        for (token in text.replace("|", " ").split(Regex("\\s+")).filter { it.isNotBlank() }) {
            val (name, len) = token.split(':').let { it[0] to (it.getOrNull(1)?.toDoubleOrNull() ?: throw TabFormatException("No length: $token")) }
            if (pos + len > beatsPerBar + 1e-9) throw TabFormatException("Note crosses a bar line: $token (bar ${bars.size})")
            val midi = if (name == "r") null else Notes.parse(name) ?: throw TabFormatException("Unknown note: $name")
            bars.last() += Timed(pos, midi, len)
            pos += len
            if (pos >= beatsPerBar - 1e-9) { bars.add(mutableListOf()); pos = 0.0 }
        }
        if (bars.last().isEmpty()) bars.removeAt(bars.size - 1) else throw TabFormatException("Last bar is not full")
        return bars
    }

    /** One chord mark per change, per bar. */
    fun parseChords(text: String, beatsPerBar: Int): List<List<ChordMark>> {
        val tokens = text.replace("|", " ").split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.size % beatsPerBar != 0) throw TabFormatException("Chord line: ${tokens.size} beats is not a whole number of bars")
        var current = ""
        return tokens.chunked(beatsPerBar).map { bar ->
            bar.mapIndexedNotNull { beat, t ->
                if (t == "." && beat > 0) null
                else {
                    if (t != ".") current = t
                    ChordMark(beat.toDouble(), current)
                }
            }
        }
    }

    /** Bass notes for the advanced level: the chord's root on beat 1 (and beat 3 in 4/4), on the low strings. */
    private fun bass(marks: List<ChordMark>, beatsPerBar: Int): List<Timed> {
        val beats = if (beatsPerBar == 4) listOf(0, 2) else listOf(0)
        return beats.mapNotNull { beat ->
            val chord = marks.lastOrNull { it.beat <= beat }?.name ?: return@mapNotNull null
            val root = Notes.parse(rootName(chord) + "2") ?: return@mapNotNull null
            // Lowest root from E2 (40) up: open E, A or D strings when possible.
            val midi = generateSequence(root) { it + 12 }.first { it >= 40 }
            Timed(beat.toDouble(), midi, 1.0)
        }
    }

    private fun rootName(chord: String): String =
        if (chord.length > 1 && (chord[1] == '#' || chord[1] == 'b')) chord.substring(0, 2) else chord.substring(0, 1)

    /** Strings and frets for one bar, with the easiest hand path ([Fingering]). */
    private fun place(notes: List<Timed>, tuning: List<Int>): List<Beat> {
        val heard = notes.filter { it.midi != null }.map { HeardNote(it.at, it.midi!!, it.length, 1f) }
        if (heard.isEmpty()) return emptyList()
        val (_, placed) = Fingering.place(heard, tuning, 0)
        return placed.groupBy { it.note.time }.entries.sortedBy { it.key }.map { (at, group) ->
            // Fingering counts strings from the lowest (0); tabs count from the high E (1).
            Beat(at, group.map { TabNote(string = 6 - it.string, fret = it.fret) }.sortedBy { it.string })
        }
    }
}
