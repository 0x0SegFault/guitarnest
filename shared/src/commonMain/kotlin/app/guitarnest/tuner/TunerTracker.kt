package app.guitarnest.tuner

import kotlin.math.abs
import kotlin.math.log2

/**
 * Keeps the tuner readable while a string rings out. A plucked string gets quieter and less clear
 * within a second; without this, the reading would vanish before the player can read it.
 * - A new note needs a clear pitch; while the same note rings, a less clear pitch is accepted.
 * - After the last good reading, the value stays for [holdMs] (shown dimmed after [freshMs]).
 */
class TunerTracker(
    private val holdMs: Long = 4_000,
    private val freshMs: Long = 300,
    private val newNoteClarity: Double = 0.85,
    private val sameNoteClarity: Double = 0.6,
) {
    /** [frequency] null: nothing to show. [fresh] false: an older reading, kept so it can be read. */
    data class Reading(val frequency: Double?, val fresh: Boolean)

    private val smoother = PitchSmoother()
    private var last: Double? = null
    private var lastAt = 0L

    fun update(pitch: Pitch?, nowMs: Long): Reading {
        val current = last
        val accepted = pitch?.takeIf { p ->
            p.clarity >= newNoteClarity ||
                (current != null && p.clarity >= sameNoteClarity && abs(1200 * log2(p.frequency / current)) < 50)
        }
        if (accepted != null) {
            last = smoother.add(accepted.frequency)
            lastAt = nowMs
            return Reading(last, true)
        }
        val age = nowMs - lastAt
        if (current == null || age > holdMs) {
            last = null
            smoother.reset()
            return Reading(null, false)
        }
        return Reading(current, age <= freshMs)
    }

    fun reset() {
        last = null
        smoother.reset()
    }
}
