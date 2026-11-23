package app.guitarnest.tab

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class PlayAlongTest {
    private val song = Song(
        title = "t",
        sections = listOf(Section("A", listOf(
            Bar(chords = listOf(ChordMark(0.0, "G"), ChordMark(2.0, "C"))),
            Bar(),
            Bar(chords = listOf(ChordMark(0.0, "D"))),
        ))),
    )
    private val order = song.playOrder()

    @Test
    fun currentAndNextChord() {
        assertEquals("G", PlayAlong.chordAt(order, 0, 1))
        assertEquals("C", PlayAlong.chordAt(order, 0, 3))
        assertEquals("C", PlayAlong.chordAt(order, 1, 0)) // no chord in the bar: the previous one goes on
        assertEquals("C", PlayAlong.nextChord(order, 0, 0))
        assertEquals("D", PlayAlong.nextChord(order, 1, 2))
        assertNull(PlayAlong.nextChord(order, 2, 0))
        assertEquals(listOf("G", "C", "D"), PlayAlong.chordsInSong(song))
    }

    @Test
    fun choosingAnotherShapeIsRemembered() {
        val first = ChordShapes.shapeFor(song, "C").toString()
        val changed = PlayAlong.nextShape(song, "C")
        assertNotEquals(first, changed.shapes["C"])
        assertEquals(changed.shapes["C"], ChordShapes.shapeFor(changed, "C").toString())
    }

    @Test
    fun pickingFollowsTheChordBass() {
        val travis = StrumPattern("t", "Travis", "B1b2B1b2", kind = StrumPattern.PICK)
        val c = ChordShape.parse("x32010")
        assertEquals(1, travis.pickString(0, c)) // bass on the A string
        assertEquals(5, travis.pickString(1, c)) // string 1 = high E
        assertEquals(2, travis.pickString(2, c)) // alternate bass: D string
        assertEquals(12, StrumPattern("s", "Soleá", "DDRDDRDRDRDR", perBeat = 1).beats)
    }
}
