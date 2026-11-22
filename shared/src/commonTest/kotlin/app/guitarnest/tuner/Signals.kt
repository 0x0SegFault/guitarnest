package app.guitarnest.tuner

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Test signals. Deterministic: the same seed gives the same samples. */
object Signals {
    fun sine(frequency: Double, sampleRate: Int, count: Int, amplitude: Double = 0.5) =
        FloatArray(count) { (amplitude * sin(2 * PI * frequency * it / sampleRate)).toFloat() }

    /** Harmonics with the given weights; weights[0] is the fundamental. */
    fun harmonics(frequency: Double, sampleRate: Int, count: Int, weights: List<Double>) =
        FloatArray(count) { i ->
            weights.withIndex().sumOf { (h, w) -> w * sin(2 * PI * frequency * (h + 1) * i / sampleRate) }
                .times(0.3).toFloat()
        }

    /**
     * Karplus-Strong plucked string: a fair model of a guitar note. An all-pass filter tunes
     * the fractional part of the period (Karplus & Strong 1983, doi:10.2307/3680062;
     * Jaffe & Smith 1983, doi:10.2307/3680063).
     */
    fun pluck(frequency: Double, sampleRate: Int, count: Int, seed: Int = 1): FloatArray {
        val random = Random(seed)
        val period = sampleRate / frequency
        // The averaging filter adds half a sample of delay.
        val length = (period - 0.5 - 0.1).toInt()
        val fraction = period - 0.5 - length
        val c = (1 - fraction) / (1 + fraction)
        val line = DoubleArray(length) { random.nextDouble(-0.5, 0.5) }
        val out = FloatArray(count)
        var index = 0
        var last = 0.0
        var apIn = 0.0
        var apOut = 0.0
        for (i in 0 until count) {
            val value = line[index]
            out[i] = value.toFloat()
            val lowPass = 0.996 * 0.5 * (value + last)
            last = value
            val allPass = c * (lowPass - apOut) + apIn
            apIn = lowPass
            apOut = allPass
            line[index] = allPass
            index = (index + 1) % length
        }
        return out
    }

    fun noise(count: Int, amplitude: Double, seed: Int = 2): FloatArray {
        val random = Random(seed)
        return FloatArray(count) { (random.nextDouble(-amplitude, amplitude)).toFloat() }
    }
}
