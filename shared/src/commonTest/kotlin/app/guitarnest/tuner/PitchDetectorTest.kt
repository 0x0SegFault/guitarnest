package app.guitarnest.tuner

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PitchDetectorTest {
    private val rate = 48_000
    private val detector = PitchDetector(rate)
    private val n = detector.windowSize

    private fun assertCents(expected: Double, actual: Double?, tolerance: Double) {
        assertNotNull(actual, "no pitch for $expected Hz")
        val cents = 1200 * kotlin.math.log2(actual / expected)
        assertTrue(abs(cents) <= tolerance, "expected $expected Hz, got $actual Hz ($cents cents)")
    }

    @Test
    fun sineOnEveryStandardString() {
        for (midi in Tuning.STANDARD.strings) {
            val f = Notes.frequencyOf(midi)
            assertCents(f, detector.detect(Signals.sine(f, rate, n))?.frequency, 1.0)
        }
    }

    @Test
    fun slightlyDetunedStringIsMeasured() {
        val f = Notes.frequencyOf(45) * 1.004 // A2, about 7 cents sharp
        assertCents(f, detector.detect(Signals.sine(f, rate, n))?.frequency, 1.0)
    }

    @Test
    fun weakFundamentalGivesNoOctaveError() {
        // Low E with a second harmonic three times stronger than the fundamental.
        val f = Notes.frequencyOf(40)
        val signal = Signals.harmonics(f, rate, n, listOf(0.3, 1.0, 0.6, 0.3, 0.2))
        assertCents(f, detector.detect(signal)?.frequency, 2.0)
    }

    @Test
    fun pluckedStringsFromDropCToHighFrets() {
        for (midi in listOf(36, 40, 45, 50, 55, 59, 64, 76, 84)) {
            val f = Notes.frequencyOf(midi)
            val pluck = Signals.pluck(f, rate, n + rate / 10).copyOfRange(rate / 10, rate / 10 + n)
            assertCents(f, detector.detect(pluck)?.frequency, 5.0)
        }
    }

    @Test
    fun noisyPluckStillDetected() {
        val f = Notes.frequencyOf(50)
        val pluck = Signals.pluck(f, rate, n)
        val noise = Signals.noise(n, 0.05)
        val mixed = FloatArray(n) { pluck[it] + noise[it] }
        assertCents(f, detector.detect(mixed)?.frequency, 5.0)
    }

    @Test
    fun silenceAndNoiseGiveNothing() {
        assertNull(detector.detect(FloatArray(n)))
        assertNull(detector.detect(Signals.noise(n, 0.005)))
        assertNull(detector.detect(Signals.noise(n, 0.5)))
    }

    @Test
    fun shortBufferGivesNothing() {
        assertNull(detector.detect(FloatArray(n - 1)))
    }

    @Test
    fun worksAt44100() {
        val d = PitchDetector(44_100)
        val f = Notes.frequencyOf(40)
        assertCents(f, d.detect(Signals.sine(f, 44_100, d.windowSize))?.frequency, 1.0)
    }
}
