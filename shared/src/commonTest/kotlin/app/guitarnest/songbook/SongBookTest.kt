package app.guitarnest.songbook

import app.guitarnest.tab.ChordShapes
import app.guitarnest.tab.Level
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SongBookTest {
    @Test
    fun everySongBuildsAtEveryLevel() {
        for (book in SongBook.songs()) for (level in Level.entries) {
            val song = SongBook.song(book, level)
            assertTrue(song.sections.single().bars.isNotEmpty(), book.id)
            assertEquals(SongBook.SOURCE, song.source)
        }
    }

    @Test
    fun everyMelodyNoteIsPlacedOnTheNeck() {
        for (book in SongBook.songs()) {
            val notes = SongBook.parseMelody(book.melody, book.beatsPerBar).flatten().count { it.midi != null }
            val bars = SongBook.song(book, Level.INTERMEDIATE).sections.single().bars
            assertEquals(notes, bars.sumOf { bar -> bar.beats.sumOf { it.notes.size } }, "${book.id}: every melody note must be playable")
            val frets = bars.flatMap { b -> b.beats.flatMap { it.notes } }.map { it.fret }
            assertTrue(frets.all { it in 0..12 }, "${book.id}: highest fret ${frets.max()}")
        }
    }

    @Test
    fun theAdvancedLevelAddsBassNotes() {
        val book = SongBook.songs().first { it.id == "ode_to_joy" }
        val advanced = SongBook.song(book, Level.ADVANCED)
        assertTrue(advanced.sections.single().bars.first().beats.first().notes.size >= 2, "melody and bass on beat 1")
        assertTrue(advanced.patterns.isEmpty(), "fingerstyle: no strumming pattern")
    }

    @Test
    fun everyChordHasADiagram() {
        for (book in SongBook.songs()) {
            val names = SongBook.parseChords(book.chords, book.beatsPerBar).flatten().map { it.name }.toSet()
            for (n in names) assertTrue(ChordShapes.voicings(n).isNotEmpty(), "${book.id}: no diagram for $n")
        }
    }

    @Test
    fun idsAreUniqueAndTheBookIsFull() {
        val ids = SongBook.songs().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.size >= 20)
    }
}
