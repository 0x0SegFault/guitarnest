package app.guitarnest.tuner

import kotlin.math.sqrt

/** A detected pitch. [clarity] is 0..1; values above 0.9 are a clean, periodic tone. */
data class Pitch(val frequency: Double, val clarity: Double)

/**
 * YIN pitch detector (de Cheveigné & Kawahara 2002, doi:10.1121/1.1458024).
 *
 * The cumulative mean normalised difference avoids most octave errors, which matter on guitar:
 * the low strings often have a weaker fundamental than their second harmonic.
 */
class PitchDetector(
    private val sampleRate: Int,
    private val minFrequency: Double = 60.0,
    private val maxFrequency: Double = 1400.0,
    private val threshold: Double = 0.15,
    /** Signals with a lower RMS level are silence. */
    private val minLevel: Double = 0.01,
) {
    /** Samples needed for one detection: two periods of the lowest note, plus the lag range. */
    val windowSize: Int = run {
        val needed = 2 * (sampleRate / minFrequency).toInt() + 1
        var size = 1024
        while (size < needed * 2) size *= 2
        size
    }

    fun detect(samples: FloatArray): Pitch? {
        if (samples.size < windowSize) return null
        if (rms(samples) < minLevel) return null

        val tauMin = (sampleRate / maxFrequency).toInt().coerceAtLeast(2)
        val tauMax = (sampleRate / minFrequency).toInt()
        val width = windowSize - tauMax - 1
        val diff = DoubleArray(tauMax + 2)
        for (tau in 1..tauMax + 1) {
            var sum = 0.0
            for (j in 0 until width) {
                val d = samples[j] - samples[j + tau]
                sum += d * d
            }
            diff[tau] = sum
        }

        // Cumulative mean normalised difference.
        val cmnd = DoubleArray(tauMax + 2)
        cmnd[0] = 1.0
        var running = 0.0
        for (tau in 1..tauMax + 1) {
            running += diff[tau]
            cmnd[tau] = if (running == 0.0) 1.0 else diff[tau] * tau / running
        }

        var tau = firstDipBelow(cmnd, tauMin, tauMax)
            ?: lowestPoint(cmnd, tauMin, tauMax).takeIf { cmnd[it] < 0.4 }
            ?: return null
        while (tau + 1 <= tauMax && cmnd[tau + 1] < cmnd[tau]) tau++

        val period = refine(cmnd, tau)
        return Pitch(sampleRate / period, (1.0 - cmnd[tau]).coerceIn(0.0, 1.0))
    }

    private fun firstDipBelow(cmnd: DoubleArray, from: Int, to: Int): Int? {
        for (tau in from..to) if (cmnd[tau] < threshold) return tau
        return null
    }

    private fun lowestPoint(cmnd: DoubleArray, from: Int, to: Int): Int {
        var best = from
        for (tau in from..to) if (cmnd[tau] < cmnd[best]) best = tau
        return best
    }

    /** Parabolic interpolation around the minimum, for sub-sample precision. */
    private fun refine(values: DoubleArray, tau: Int): Double {
        if (tau < 1 || tau + 1 >= values.size) return tau.toDouble()
        val left = values[tau - 1]
        val centre = values[tau]
        val right = values[tau + 1]
        val denominator = left - 2 * centre + right
        if (denominator == 0.0) return tau.toDouble()
        return tau + 0.5 * (left - right) / denominator
    }

    private fun rms(samples: FloatArray): Double {
        var sum = 0.0
        for (i in 0 until windowSize) sum += samples[i] * samples[i]
        return sqrt(sum / windowSize)
    }
}
