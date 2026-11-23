package app.guitarnest.tab

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChordShapesTest {
    private fun shapes(name: String) = ChordShapes.voicings(name).map { it.toString() }

    @Test
    fun commonOpenShapesComeFirst() {
        assertEquals("x32010", shapes("C").first())
        assertEquals("xx0232", shapes("D").first())
        assertEquals("022000", shapes("Em").first())
        assertEquals("x02210", shapes("Am").first())
        assertEquals("022100", shapes("E").first())
        assertTrue(shapes("G").first() in setOf("320003", "320033", "355433"))
    }

    @Test
    fun barreShapesUpTheNeck() {
        val f = shapes("F")
        assertTrue("133211" in f, f.toString())
        val c = shapes("C")
        assertTrue(c.any { it.startsWith("x35553") || it == "x3555x" || it == "8-10-10-9-8-8" }, c.toString())
        assertEquals(Triple(1, 0, 5), ChordShape.parse("133211")!!.barre)
        assertNull(ChordShape.parse("x32010")!!.barre)
        assertNull(ChordShape.parse("022000")!!.barre)
        assertNull(ChordShape.parse("xx0232")!!.barre)
        assertEquals(Triple(2, 1, 5), ChordShape.parse("x24432")!!.barre)
    }

    @Test
    fun everyVoicingPlaysTheChordWithTheRightBass() {
        for (name in listOf("A", "Bm", "F#m", "Bb", "C7", "Gmaj7", "Dsus4", "E5", "D/F#", "Am7", "Cadd9")) {
            val (root, tones, bass) = ChordShapes.parse(name)!!
            val list = ChordShapes.voicings(name)
            assertTrue(list.isNotEmpty(), name)
            for (s in list) {
                val notes = s.frets.indices.filter { s.frets[it] >= 0 }.map { ChordShapes.STANDARD[it] + s.frets[it] }
                assertTrue(notes.all { it % 12 in tones }, "$name $s")
                assertEquals(bass, notes.first() % 12, "$name $s bass")
                assertTrue(root in notes.map { it % 12 }, "$name $s root")
            }
        }
    }

    @Test
    fun parseAndCapoRelativeSongShapes() {
        assertEquals(listOf(-1, 3, 2, 0, 1, 0), ChordShape.parse("x32010")!!.frets)
        assertEquals("8-10-10-9-8-8", ChordShape.parse("8-10-10-9-8-8")!!.toString())
        assertNull(ChordShape.parse("x320"))
        assertNull(ChordShapes.parse("H7"))
        val song = Song(title = "s", shapes = mapOf("G" to "355433"))
        assertEquals("355433", ChordShapes.shapeFor(song, "G").toString())
    }
}
