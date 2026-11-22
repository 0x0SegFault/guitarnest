package app.guitarnest.tab

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SongTest {
    private val song = Song(
        title = "Test song",
        artist = "Me",
        tuning = "drop_d",
        capo = 2,
        tempo = 96,
        patterns = listOf(StrumPattern("a", "Folk", "D-DU-UDU")),
        sections = listOf(
            Section(
                "Verse", repeat = 2, strum = "a",
                bars = listOf(
                    Bar(chords = listOf(ChordMark(0.0, "G"), ChordMark(2.0, "C"))),
                    Bar(chords = listOf(ChordMark(0.0, "D")), beats = listOf(Beat(0.0, listOf(TabNote(6, 0))), Beat(1.5, listOf(TabNote(3, 2, "h"))))),
                ),
            ),
        ),
    )

    @Test
    fun jsonRoundTrip() {
        val text = SongCodec.encode(song)
        assertTrue(text.contains("\"tuning\": \"drop_d\""))
        assertEquals(song, SongCodec.decode(text))
    }

    @Test
    fun unknownFieldsAreIgnoredAndNewerFormatRefused() {
        val text = SongCodec.encode(song).replaceFirst("{", "{\n  \"future\": 1,")
        assertEquals(song, SongCodec.decode(text))
        assertFailsWith<TabFormatException> { SongCodec.decode("""{"format": 99, "title": "x"}""") }
        assertFailsWith<TabFormatException> { SongCodec.decode("not json") }
    }

    @Test
    fun validationFindsProblems() {
        val bad = song.copy(capo = 20, sections = listOf(Section("X", strum = "nope", bars = listOf(Bar(beats = listOf(Beat(0.0, listOf(TabNote(7, 30)))))))))
        val problems = SongCodec.validate(bad)
        assertTrue(problems.any { "Capo" in it })
        assertTrue(problems.any { "unknown pattern" in it })
        assertTrue(problems.any { "string 7" in it })
        assertTrue(problems.any { "fret 30" in it })
    }

    @Test
    fun customTuning() {
        assertEquals(listOf(38, 45, 50, 55, 59, 64), song.copy(tuning = "D2 A2 D3 G3 B3 E4").tuningNotes()!!.strings)
        assertEquals(null, song.copy(tuning = "D2 A2").tuningNotes())
    }

    @Test
    fun playOrderExpandsRepeats() {
        val order = song.playOrder()
        assertEquals(4, order.size)
        assertEquals(1, order[3].pass)
        assertEquals("a", order[0].strum)
    }

    @Test
    fun strumPatternRepeats() {
        val p = song.patterns[0]
        assertEquals('D', p.strokeAt(0))
        assertEquals('-', p.strokeAt(1))
        assertEquals('U', p.strokeAt(11))
        assertTrue(p.handDown(2))
    }
}
