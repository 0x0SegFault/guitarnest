package app.guitarnest.listen

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Tempo, beat grid, chords and strumming from heard notes. Port of ai/gntab/analyse.py:
 * - tempo: autocorrelation of the onset train, mild preference for 70-140 BPM, then tempo and phase fitted together;
 * - chords: pitch-class profile per beat against chord templates;
 * - strumming: strums on an eighth-note grid; down on the beat, up between beats.
 */
object Analysis {
    val NAMES = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
    private val TEMPLATES = listOf("" to intArrayOf(0, 4, 7), "m" to intArrayOf(0, 3, 7), "7" to intArrayOf(0, 4, 7, 10), "m7" to intArrayOf(0, 3, 7, 10), "5" to intArrayOf(0, 7))
    private val TYPE_COST = mapOf("" to 0.0, "m" to 0.0, "7" to 0.15, "m7" to 0.15, "5" to 0.3)

    data class Grid(val bpm: Double, val firstBeat: Double, val beatsPerBar: Int = 4) {
        val beat: Double get() = 60.0 / bpm
        fun beatTime(i: Int) = firstBeat + i * beat
    }

    private fun events(notes: List<HeardNote>) = Fingering.groups(notes).map { it[0].time to it.size.toDouble() }

    fun estimateTempo(notes: List<HeardNote>, low: Double = 50.0, high: Double = 200.0): Double {
        val ev = events(notes)
        if (ev.size < 4) return 90.0
        val rate = 100
        val end = (ev.maxOf { it.first } * rate).toInt() + 2
        val raw = DoubleArray(end)
        for ((t, w) in ev) raw[(t * rate).toInt()] += w
        // Hanning window of 5 (numpy.hanning(5) = 0, .5, 1, .5, 0), centred.
        val han = doubleArrayOf(0.0, 0.5, 1.0, 0.5, 0.0)
        val train = DoubleArray(end) { i -> (0 until 5).sumOf { k -> val j = i + k - 2; if (j in 0 until end) raw[j] * han[k] else 0.0 } }
        fun dot(lag: Int): Double { var s = 0.0; for (i in 0 until end - lag) s += train[i] * train[i + lag]; return s }
        var best = 90.0; var bestScore = -1.0
        var bpm = low
        while (bpm < high) {
            val lag = 60.0 / bpm * rate
            val lo = floor(lag).toInt(); val hi = ceil(lag).toInt()
            if (hi < end) {
                val frac = lag - lo
                var score = (1 - frac) * dot(lo) + frac * dot(hi)
                val octave = ln(bpm / 100.0) / ln(2.0) / 0.6
                score *= exp(-0.5 * octave * octave)
                if (score > bestScore) { best = bpm; bestScore = score }
            }
            bpm += 0.5
        }
        return best
    }

    /** Tempo and phase fitted together: a small tempo error adds up over a song. */
    fun beatGrid(notes: List<HeardNote>, bpm: Double, beatsPerBar: Int = 4): Grid {
        val ev = events(notes)
        val first = ev.minOf { it.first }
        var best = Triple(-1.0, bpm, 0.0)
        var b = bpm - 2
        while (b <= bpm + 2.001) {
            val beat = 60.0 / b
            var phase = 0.0
            while (phase < beat) {
                var score = 0.0
                for ((t, w) in ev) {
                    val d = (t - first - phase) / beat
                    val dist = abs(d - d.roundToInt()) * beat / 0.025
                    score += w * exp(-dist * dist)
                }
                if (score > best.first) best = Triple(score, b, phase)
                phase += 0.005
            }
            b += 0.05
        }
        val beat = 60.0 / best.second
        var start = first + best.third
        while (start - beat >= first - 0.04) start -= beat
        return Grid(best.second, start, beatsPerBar)
    }

    fun chroma(notes: List<HeardNote>, start: Double, end: Double): DoubleArray {
        val c = DoubleArray(12)
        var lowest: Int? = null
        for (n in notes) {
            val overlap = minOf(end, n.time + n.duration) - maxOf(start, n.time)
            if (overlap <= 0) continue
            c[n.midi % 12] += overlap * n.strength
            if (lowest == null || n.midi < lowest) lowest = n.midi
        }
        lowest?.let { c[it % 12] *= 1.5 }
        val sum = c.sum()
        return if (sum > 0) DoubleArray(12) { c[it] / sum } else c
    }

    fun nameChord(c: DoubleArray): String? {
        if (c.sum() == 0.0) return null
        var best: String? = null; var score = -9.0
        for (root in 0 until 12) for ((kind, tones) in TEMPLATES) {
            val inside = tones.map { (root + it) % 12 }.toSet()
            var s = 0.0
            for (k in 0 until 12) s += if (k in inside) c[k] else -c[k]
            s -= TYPE_COST.getValue(kind) * 0.2
            if (s > score) { best = NAMES[root] + kind; score = s }
        }
        return best
    }

    fun chordsPerBeat(notes: List<HeardNote>, grid: Grid, beats: Int): List<String?> =
        List(beats) { nameChord(chroma(notes, grid.beatTime(it), grid.beatTime(it + 1))) }

    /** Beat offset where chord changes most often fall on the bar line. */
    fun alignDownbeat(perBeat: List<String?>, beatsPerBar: Int): Int {
        val counts = IntArray(beatsPerBar)
        for (i in 1 until perBeat.size) if (perBeat[i] != null && perBeat[i] != perBeat[i - 1]) counts[i % beatsPerBar]++
        return if (counts.all { it == 0 }) 0 else counts.indices.maxBy { counts[it] * 1000 - it }
    }

    /** One bar of strumming on an eighth-note grid: D on hit on-beat steps, U on hit off-beat steps. */
    fun strumSteps(notes: List<HeardNote>, grid: Grid, bar: Int, perBeat: Int = 2, minNotes: Int = 3): String {
        val steps = grid.beatsPerBar * perBeat
        val stepLength = grid.beat / perBeat
        val barStart = grid.beatTime(bar * grid.beatsPerBar)
        val hit = BooleanArray(steps)
        for (g in Fingering.groups(notes)) {
            if (g.size < minNotes) continue
            val pos = (g[0].time - barStart) / stepLength
            val k = pos.roundToInt()
            if (k in 0 until steps && abs(pos - k) < 0.3) hit[k] = true
        }
        return String(CharArray(steps) { k -> if (!hit[k]) '-' else if (k % 2 == 0) 'D' else 'U' })
    }

}
