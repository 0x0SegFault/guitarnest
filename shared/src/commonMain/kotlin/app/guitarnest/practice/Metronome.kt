package app.guitarnest.practice

import kotlin.math.floor
import kotlin.math.roundToLong

enum class ClickKind { BAR, BEAT, SUBDIVISION }

data class Click(val sample: Long, val kind: ClickKind)

/** Position in the music at a moment. [step] counts subdivisions in the bar. */
data class Position(val bar: Int, val beat: Int, val step: Int, val fraction: Double)

/**
 * Sample-exact click times. Each click is computed from the start, never by adding intervals,
 * so the timing never drifts.
 */
class ClickTrack(
    val bpm: Double,
    val beatsPerBar: Int,
    /** Subdivisions per beat that also click (1 = beats only). */
    val subdivisions: Int = 1,
    val sampleRate: Int = 48_000,
) {
    init {
        require(bpm > 0 && beatsPerBar > 0 && subdivisions > 0)
    }

    val samplesPerBeat: Double = sampleRate * 60.0 / bpm
    private val samplesPerStep = samplesPerBeat / subdivisions

    fun clicksIn(from: Long, until: Long): List<Click> {
        val out = mutableListOf<Click>()
        var n = maxOf(0L, kotlin.math.ceil(from / samplesPerStep).toLong())
        while (true) {
            val at = (n * samplesPerStep).roundToLong()
            if (at >= until) break
            if (at >= from) {
                val kind = when {
                    n % (subdivisions * beatsPerBar) == 0L -> ClickKind.BAR
                    n % subdivisions == 0L -> ClickKind.BEAT
                    else -> ClickKind.SUBDIVISION
                }
                out += Click(at, kind)
            }
            n++
        }
        return out
    }

    /** Where the music is at [sample], with [stepsPerBeat] steps per beat (for strumming patterns). */
    fun positionAt(sample: Long, stepsPerBeat: Int = 2): Position {
        val beats = sample / samplesPerBeat
        val whole = floor(beats).toLong()
        val bar = (whole / beatsPerBar).toInt()
        val beat = (whole % beatsPerBar).toInt()
        val step = floor((beats - bar.toLong() * beatsPerBar) * stepsPerBeat).toInt()
        return Position(bar, beat, step, beats - whole)
    }
}

/** Tempo from taps: median of the last intervals, so one late tap does not matter. */
class TapTempo(private val keep: Int = 6, private val resetAfterMs: Long = 2_000) {
    private val taps = ArrayDeque<Long>()

    /** Returns the tempo in BPM once there are at least 3 taps. */
    fun tap(timeMs: Long): Int? {
        if (taps.isNotEmpty() && timeMs - taps.last() > resetAfterMs) taps.clear()
        taps.addLast(timeMs)
        if (taps.size > keep) taps.removeFirst()
        if (taps.size < 3) return null
        val intervals = taps.zipWithNext { a, b -> b - a }.sorted()
        val median = intervals[intervals.size / 2]
        return if (median <= 0) null else (60_000.0 / median).toInt().coerceIn(MIN_BPM, MAX_BPM)
    }
}

/**
 * Slow practice, then speed up: start at [start] BPM and add [step] BPM every [barsPerStep] bars
 * until [target]. See docs/SCIENCE.md (Allingham & Wöllner 2022; Duke et al. 2009).
 */
data class SpeedTrainer(val start: Int, val target: Int, val step: Int = 5, val barsPerStep: Int = 4) {
    fun tempoForBar(bar: Int): Int {
        val raised = start + step * (bar / barsPerStep.coerceAtLeast(1))
        return if (target >= start) minOf(raised, target) else start
    }
}

/** Common strumming and fingerpicking patterns (generic rhythms, not from any song). */
val PRESET_PATTERNS = listOf(
    app.guitarnest.tab.StrumPattern("downs", "Downstrokes", "D-D-D-D-"),
    app.guitarnest.tab.StrumPattern("folk", "Folk", "D-DU-UDU"),
    app.guitarnest.tab.StrumPattern("pop", "Pop", "D-DUDUDU"),
    app.guitarnest.tab.StrumPattern("eighths", "Eighths", "DUDUDUDU"),
    app.guitarnest.tab.StrumPattern("waltz", "Waltz (3/4)", "D-DUDU"),
    app.guitarnest.tab.StrumPattern("sixteenths", "Sixteenths", "D-DUD-DUD-DUD-DU", perBeat = 4),
    // Flamenco. R = rasgueado on the accents of the compás (the rhythm cycle of each style).
    app.guitarnest.tab.StrumPattern("rumba", "Rumba (flamenco)", "DUxUDUxU"),
    app.guitarnest.tab.StrumPattern("backbeat", "Muted backbeat (rumba, island pop)", "D-xU-UxU"),
    app.guitarnest.tab.StrumPattern("tangos", "Tangos (flamenco)", "--D-DUR-"),
    app.guitarnest.tab.StrumPattern("garrotin", "Garrotín", "D-DUR-DU"),
    app.guitarnest.tab.StrumPattern("rasgueado", "Rasgueado", "R-DUR-DU"),
    // 12-beat compás. Soleá and alegrías: accents on 3, 6, 8, 10, 12.
    app.guitarnest.tab.StrumPattern("solea", "Soleá, alegrías (12-beat compás)", "DDRDDRDRDRDR", perBeat = 1),
    // Bulerías and guajira: the same accents, counted from 12 (12, 3, 6, 8, 10).
    app.guitarnest.tab.StrumPattern("bulerias", "Bulerías, guajira (12-beat compás)", "RDDRDDRDRDRD", perBeat = 1),
    // Seguiriya: five uneven beats, short-short-long-long-short (2+2+3+3+2): accents on 1, 3, 5, 8, 11.
    app.guitarnest.tab.StrumPattern("seguiriya", "Seguiriya (12-beat compás)", "RDRDRDDRDDRD", perBeat = 1),
    // 3-beat styles: the accent on 1.
    app.guitarnest.tab.StrumPattern("sevillanas", "Sevillanas (3/4)", "R-D-DU"),
    app.guitarnest.tab.StrumPattern("fandango", "Fandango de Huelva (3/4)", "R-DUDU"),
    // Tanguillo: 6 beats, accents on 1 and 4.
    app.guitarnest.tab.StrumPattern("tanguillo", "Tanguillo (6/8)", "RDDRDD", perBeat = 1),
    // Fingerpicking. B = bass string of the chord, b = alternate bass, 1-6 = string (1 = high E).
    app.guitarnest.tab.StrumPattern("arpeggio", "Arpeggio", "B3212321", kind = app.guitarnest.tab.StrumPattern.PICK),
    app.guitarnest.tab.StrumPattern("travis", "Travis picking", "B1b2B1b2", kind = app.guitarnest.tab.StrumPattern.PICK),
    app.guitarnest.tab.StrumPattern("waltz_pick", "Waltz picking (3/4)", "B32123", kind = app.guitarnest.tab.StrumPattern.PICK),
)

const val MIN_BPM = 30
const val MAX_BPM = 250
