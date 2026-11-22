package app.guitarnest.listen

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Sample-rate conversion with a windowed-sinc filter (Blackman window), good enough for note detection. */
object Resample {
    fun toRate(input: FloatArray, from: Int, to: Int, taps: Int = 16): FloatArray {
        if (from == to) return input
        val ratio = to.toDouble() / from
        val cutoff = minOf(1.0, ratio) * 0.95 // below the new Nyquist frequency
        val out = FloatArray((input.size * ratio).toInt())
        for (i in out.indices) {
            val center = i / ratio
            val first = floor(center).toInt() - taps + 1
            var sum = 0.0
            var norm = 0.0
            for (k in first until first + 2 * taps) {
                val x = center - k
                val sinc = if (x == 0.0) 1.0 else sin(PI * x * cutoff) / (PI * x * cutoff)
                val w = 0.42 + 0.5 * cos(PI * x / taps) + 0.08 * cos(2 * PI * x / taps)
                val coefficient = sinc * if (kotlin.math.abs(x) < taps) w else 0.0
                norm += coefficient
                if (k in input.indices) sum += input[k] * coefficient
            }
            out[i] = if (norm != 0.0) (sum / norm).toFloat() else 0f
        }
        return out
    }

    /** Mono from interleaved channels. */
    fun mono(interleaved: FloatArray, channels: Int): FloatArray =
        if (channels <= 1) interleaved else FloatArray(interleaved.size / channels) { i ->
            var s = 0f
            for (c in 0 until channels) s += interleaved[i * channels + c]
            s / channels
        }
}
