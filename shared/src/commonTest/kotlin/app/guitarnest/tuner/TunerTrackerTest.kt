package app.guitarnest.tuner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TunerTrackerTest {
    @Test
    fun readingStaysWhileTheStringRingsOut() {
        val t = TunerTracker()
        assertEquals(110.0, t.update(Pitch(110.0, 0.95), 0).frequency)
        // The string decays: a weaker pitch near the same note is still accepted.
        assertTrue(t.update(Pitch(110.2, 0.65), 100).fresh)
        // Silence: the value stays, first fresh, then dimmed, then gone after 4 s.
        assertTrue(t.update(null, 300).fresh)
        val held = t.update(null, 1_000)
        assertFalse(held.fresh)
        assertTrue(held.frequency != null)
        assertNull(t.update(null, 4_200).frequency)
    }

    @Test
    fun unclearNewNoteIsIgnored() {
        val t = TunerTracker()
        assertNull(t.update(Pitch(82.0, 0.7), 0).frequency)
        t.update(Pitch(110.0, 0.9), 10)
        // An unclear pitch far from the current note does not move the needle.
        assertEquals(110.0, t.update(Pitch(147.0, 0.7), 20).frequency)
    }
}
