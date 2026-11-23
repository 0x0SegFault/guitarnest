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

class ToneSynthTest {
    @Test
    fun soundsHaveTheRightPitch() {
        val rate = 48_000
        val detector = PitchDetector(rate)
        for (f in listOf(82.41, 110.0, 440.0)) {
            val pluck = ToneSynth.pluck(f, rate)
            val got = detector.detect(pluck.copyOfRange(rate / 4, rate / 4 + detector.windowSize))!!.frequency
            assertTrue(kotlin.math.abs(1200 * kotlin.math.log2(got / f)) < 5, "pluck $f -> $got")
        }
        val fork = ToneSynth.fork(440.0, rate)
        assertTrue(kotlin.math.abs(detector.detect(fork.copyOfRange(rate, rate + detector.windowSize))!!.frequency - 440.0) < 0.5)
    }
}
