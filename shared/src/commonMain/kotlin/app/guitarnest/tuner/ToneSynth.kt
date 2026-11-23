package app.guitarnest.tuner

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Reference sounds for the tuner: a plucked string (to tune by ear) and a tuning fork. */
object ToneSynth {
    /** A plucked string (Karplus-Strong with fractional-delay tuning), [seconds] long, peak 0.8. */
    fun pluck(frequency: Double, sampleRate: Int, seconds: Double = 2.5): FloatArray {
        val random = Random(frequency.toInt())
        val period = sampleRate / frequency
        val length = (period - 0.6).toInt().coerceAtLeast(2)
        val frac = period - 0.5 - length
        val c = (1 - frac) / (1 + frac)
        val line = DoubleArray(length) { random.nextDouble(-1.0, 1.0) }
        val out = FloatArray((sampleRate * seconds).toInt())
        var index = 0; var last = 0.0; var apIn = 0.0; var apOut = 0.0
        for (i in out.indices) {
            val v = line[index]
            out[i] = v.toFloat()
            val lowPass = 0.998 * 0.5 * (v + last)
            last = v
            val allPass = c * (lowPass - apOut) + apIn
            apIn = lowPass; apOut = allPass
            line[index] = allPass
            index = (index + 1) % length
        }
        return fade(normalise(out))
    }

    /** A tuning fork: a pure tone that dies away slowly. */
    fun fork(frequency: Double, sampleRate: Int, seconds: Double = 3.0): FloatArray {
        val out = FloatArray((sampleRate * seconds).toInt()) { i ->
            val t = i.toDouble() / sampleRate
            (0.6 * exp(-t * 1.2) * sin(2 * PI * frequency * t) * minOf(1.0, t * 200)).toFloat()
        }
        return fade(out)
    }

    private fun normalise(x: FloatArray): FloatArray {
        val peak = x.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-6f)
        return FloatArray(x.size) { x[it] / peak * 0.8f }
    }

    private fun fade(x: FloatArray): FloatArray {
        val n = minOf(x.size, 2_000)
        for (i in 0 until n) x[x.size - n + i] *= 1f - i.toFloat() / n
        return x
    }
}
