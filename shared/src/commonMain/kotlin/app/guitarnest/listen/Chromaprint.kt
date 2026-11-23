package app.guitarnest.listen

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Audio fingerprint in the Chromaprint format (algorithm 2, the fpcalc default), for AcoustID lookups.
 * Port of the MIT-licensed core of Chromaprint by Lukáš Lalinský: https://github.com/acoustid/chromaprint
 * Input: mono samples at [SAMPLE_RATE] in -1..1. Use [Resample] first.
 */
object Chromaprint {
    const val SAMPLE_RATE = 11025
    /** AcoustID needs at least this much audio; fpcalc uses the first 120 s. */
    const val MIN_SECONDS = 10
    const val MAX_SECONDS = 120

    private const val FRAME = 4096
    private const val STEP = FRAME / 3
    private const val BANDS = 12
    private const val MIN_FREQ = 28.0
    private const val MAX_FREQ = 3520.0
    private const val ALGORITHM = 1 // index of "TEST2" in Chromaprint
    private val FILTER_COEFFICIENTS = doubleArrayOf(0.25, 0.75, 1.0, 0.75, 0.25)

    private class Classifier(val type: Int, val y: Int, val height: Int, val width: Int, val t0: Double, val t1: Double, val t2: Double)

    private val CLASSIFIERS = listOf(
        Classifier(0, 4, 3, 15, 1.98215, 2.35817, 2.63523),
        Classifier(4, 4, 6, 15, -1.03809, -0.651211, -0.282167),
        Classifier(1, 0, 4, 16, -0.298702, 0.119262, 0.558497),
        Classifier(3, 8, 2, 12, -0.105439, 0.0153946, 0.135898),
        Classifier(3, 4, 4, 8, -0.142891, 0.0258736, 0.200632),
        Classifier(4, 0, 3, 5, -0.826319, -0.590612, -0.368214),
        Classifier(1, 2, 2, 9, -0.557409, -0.233035, 0.0534525),
        Classifier(2, 7, 3, 4, -0.0646826, 0.00620476, 0.0784847),
        Classifier(2, 6, 2, 16, -0.192387, -0.029699, 0.215855),
        Classifier(2, 1, 3, 2, -0.0397818, -0.00568076, 0.0292026),
        Classifier(5, 10, 1, 15, -0.53823, -0.369934, -0.190235),
        Classifier(3, 6, 2, 10, -0.124877, 0.0296483, 0.139239),
        Classifier(2, 1, 1, 14, -0.101475, 0.0225617, 0.231971),
        Classifier(3, 5, 6, 4, -0.0799915, -0.00729616, 0.063262),
        Classifier(1, 9, 2, 12, -0.272556, 0.019424, 0.302559),
        Classifier(3, 4, 2, 14, -0.164292, -0.0321188, 0.0846339),
    )
    private val MAX_WIDTH = CLASSIFIERS.maxOf { it.width }

    /** Raw fingerprint: one 32-bit value per 1365 samples (about 0.124 s). */
    fun raw(samples: FloatArray): IntArray {
        val chroma = filteredChroma(samples)
        val rows = chroma.size
        if (rows < MAX_WIDTH) return IntArray(0)
        val image = integral(chroma)
        return IntArray(rows - MAX_WIDTH + 1) { x ->
            var bits = 0
            for (c in CLASSIFIERS) bits = (bits shl 2) or GRAY[quantize(c, apply(c, image, x))]
            bits
        }
    }

    /** Compressed fingerprint, as fpcalc prints it and AcoustID expects it. */
    fun encode(samples: FloatArray): String = compress(raw(samples))

    private val GRAY = intArrayOf(0, 1, 3, 2)

    private fun filteredChroma(samples: FloatArray): List<DoubleArray> {
        // Chromaprint works on 16-bit samples with a window scaled by 1/32767.
        val pcm = DoubleArray(samples.size) { round(samples[it] * 32768.0).coerceIn(-32768.0, 32767.0) }
        val window = DoubleArray(FRAME) { (0.54 - 0.46 * cos(it * 2.0 * PI / (FRAME - 1))) / 32767.0 }
        val minIndex = maxOf(1, round(FRAME * MIN_FREQ / SAMPLE_RATE).toInt())
        val maxIndex = minOf(FRAME / 2, round(FRAME * MAX_FREQ / SAMPLE_RATE).toInt())
        val notes = IntArray(FRAME) { i ->
            if (i < minIndex || i >= maxIndex) 0 else {
                val octave = ln(i.toDouble() * SAMPLE_RATE / FRAME / (440.0 / 16.0)) / ln(2.0)
                (BANDS * (octave - floor(octave))).toInt()
            }
        }
        val re = DoubleArray(FRAME)
        val im = DoubleArray(FRAME)
        val raw = ArrayList<DoubleArray>()
        var start = 0
        while (start + FRAME <= pcm.size) {
            for (i in 0 until FRAME) { re[i] = pcm[start + i] * window[i]; im[i] = 0.0 }
            fft(re, im)
            val features = DoubleArray(BANDS)
            for (i in minIndex until maxIndex) features[notes[i]] += re[i] * re[i] + im[i] * im[i]
            raw += features
            start += STEP
        }
        // Smoothing over 5 frames; the first output uses frames 1 to 5.
        val n = FILTER_COEFFICIENTS.size
        val out = ArrayList<DoubleArray>()
        for (end in n..raw.size) {
            val features = DoubleArray(BANDS)
            for (b in 0 until BANDS) for (j in 0 until n) features[b] += raw[end - n + j][b] * FILTER_COEFFICIENTS[j]
            val norm = sqrt(features.sumOf { it * it })
            if (norm < 0.01) features.fill(0.0) else for (b in 0 until BANDS) features[b] /= norm
            out += features
        }
        return out
    }

    /** Integral image with one extra row and column of zeros: rows are time, columns are chroma bands. */
    private fun integral(rows: List<DoubleArray>): Array<DoubleArray> {
        val image = Array(rows.size + 1) { DoubleArray(BANDS + 1) }
        for (r in rows.indices) for (c in 0 until BANDS) {
            image[r + 1][c + 1] = rows[r][c] + image[r][c + 1] + image[r + 1][c] - image[r][c]
        }
        return image
    }

    private fun area(image: Array<DoubleArray>, r1: Int, c1: Int, r2: Int, c2: Int): Double =
        image[r2][c2] - image[r1][c2] - image[r2][c1] + image[r1][c1]

    private fun apply(c: Classifier, image: Array<DoubleArray>, x: Int): Double {
        val y = c.y
        val w = c.width
        val h = c.height
        fun a(r1: Int, c1: Int, r2: Int, c2: Int) = area(image, r1, c1, r2, c2)
        val (first, second) = when (c.type) {
            0 -> a(x, y, x + w, y + h) to 0.0
            1 -> a(x, y + h / 2, x + w, y + h) to a(x, y, x + w, y + h / 2)
            2 -> a(x + w / 2, y, x + w, y + h) to a(x, y, x + w / 2, y + h)
            3 -> (a(x, y + h / 2, x + w / 2, y + h) + a(x + w / 2, y, x + w, y + h / 2)) to
                (a(x, y, x + w / 2, y + h / 2) + a(x + w / 2, y + h / 2, x + w, y + h))
            4 -> a(x, y + h / 3, x + w, y + 2 * (h / 3)) to (a(x, y, x + w, y + h / 3) + a(x, y + 2 * (h / 3), x + w, y + h))
            else -> a(x + w / 3, y, x + 2 * (w / 3), y + h) to (a(x, y, x + w / 3, y + h) + a(x + 2 * (w / 3), y, x + w, y + h))
        }
        return ln((1.0 + first) / (1.0 + second))
    }

    private fun quantize(c: Classifier, v: Double): Int = when {
        v < c.t1 -> if (v < c.t0) 0 else 1
        v < c.t2 -> 2
        else -> 3
    }

    internal fun compress(fp: IntArray): String {
        val normal = ArrayList<Int>()
        val exceptional = ArrayList<Int>()
        for (i in fp.indices) {
            var x = if (i == 0) fp[0] else fp[i] xor fp[i - 1]
            var bit = 1
            var last = 0
            while (x != 0) {
                if (x and 1 != 0) {
                    val value = bit - last
                    if (value >= 7) { normal += 7; exceptional += value - 7 } else normal += value
                    last = bit
                }
                x = x ushr 1
                bit++
            }
            normal += 0
        }
        val bytes = ArrayList<Byte>()
        bytes += ALGORITHM.toByte()
        bytes += (fp.size shr 16).toByte()
        bytes += (fp.size shr 8).toByte()
        bytes += fp.size.toByte()
        pack(normal, 3, bytes)
        pack(exceptional, 5, bytes)
        return base64(bytes.toByteArray())
    }

    /** Packs values LSB first, as Chromaprint's PackInt3Array and PackInt5Array do. */
    private fun pack(values: List<Int>, bits: Int, out: MutableList<Byte>) {
        var acc = 0L
        var count = 0
        for (v in values) {
            acc = acc or ((v.toLong() and ((1L shl bits) - 1)) shl count)
            count += bits
            while (count >= 8) { out += (acc and 0xFF).toByte(); acc = acc ushr 8; count -= 8 }
        }
        if (count > 0) out += (acc and 0xFF).toByte()
    }

    private const val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    /** URL-safe base64 without padding. */
    private fun base64(data: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val b1 = if (i + 1 < data.size) data[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < data.size) data[i + 2].toInt() and 0xFF else 0
            sb.append(B64[b0 shr 2])
            sb.append(B64[((b0 and 3) shl 4) or (b1 shr 4)])
            if (i + 1 < data.size) sb.append(B64[((b1 and 15) shl 2) or (b2 shr 6)])
            if (i + 2 < data.size) sb.append(B64[b2 and 63])
            i += 3
        }
        return sb.toString()
    }

    /** In-place radix-2 FFT. */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2 * PI / len
            val wr = cos(angle)
            val wi = sin(angle)
            var i = 0
            while (i < n) {
                var cr = 1.0
                var ci = 0.0
                for (k in 0 until len / 2) {
                    val a = i + k
                    val b = a + len / 2
                    val xr = re[b] * cr - im[b] * ci
                    val xi = re[b] * ci + im[b] * cr
                    re[b] = re[a] - xr; im[b] = im[a] - xi
                    re[a] += xr; im[a] += xi
                    val t = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = t
                }
                i += len
            }
            len = len shl 1
        }
    }
}
