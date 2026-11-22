package app.guitarnest.tuner

/**
 * Median of the last readings. Removes single-frame jumps (pick attack, octave slips)
 * so the needle does not shake. Resets when the note changes by more than a semitone.
 */
class PitchSmoother(private val size: Int = 5) {
    private val recent = ArrayDeque<Double>()

    fun add(frequency: Double): Double {
        val last = recent.lastOrNull()
        if (last != null && (frequency / last > SEMITONE || last / frequency > SEMITONE)) recent.clear()
        recent.addLast(frequency)
        if (recent.size > size) recent.removeFirst()
        return recent.sorted()[recent.size / 2]
    }

    fun reset() = recent.clear()

    private companion object {
        const val SEMITONE = 1.0595
    }
}
