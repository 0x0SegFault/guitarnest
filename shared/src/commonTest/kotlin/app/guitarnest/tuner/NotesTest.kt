package app.guitarnest.tuner

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotesTest {
    @Test
    fun namesAndParsing() {
        assertEquals("A4", Notes.name(69))
        assertEquals("E2", Notes.name(40))
        assertEquals(40, Notes.parse("E2"))
        assertEquals(66, Notes.parse("F#4"))
        assertEquals(66, Notes.parse("Gb4"))
        assertEquals(61, Notes.parse("C♯4"))
        assertNull(Notes.parse("H2"))
    }

    @Test
    fun frequencies() {
        assertTrue(abs(Notes.frequencyOf(40) - 82.4069) < 0.001)
        assertTrue(abs(Notes.frequencyOf(69, a4 = 432.0) - 432.0) < 1e-9)
    }

    @Test
    fun readingGivesNearestNoteAndCents() {
        val r = readingOf(Notes.frequencyOf(45) * 1.01)
        assertEquals("A2", r.name)
        assertTrue(abs(r.cents - 17.2) < 0.1)
        val flat = readingOf(Notes.frequencyOf(45) * 0.99)
        assertEquals(45, flat.midi)
        assertTrue(flat.cents < 0)
    }

    @Test
    fun otherReferencePitch() {
        assertEquals(69, readingOf(432.0, a4 = 432.0).midi)
        assertTrue(abs(readingOf(432.0, a4 = 432.0).cents) < 1e-6)
    }
}
