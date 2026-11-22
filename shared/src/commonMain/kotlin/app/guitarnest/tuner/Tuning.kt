package app.guitarnest.tuner

import kotlin.math.abs

/** Open-string notes as MIDI numbers, lowest string first. */
data class Tuning(val id: String, val strings: List<Int>) {
    val names: List<String> get() = strings.map(Notes::name)

    /** Sounding notes of the open strings with a capo on [fret]. */
    fun withCapo(fret: Int): List<Int> = strings.map { it + fret }

    /** The string the player is most likely tuning: nearest in pitch. */
    fun nearestString(frequency: Double, a4: Double = 440.0): Int {
        val midi = Notes.midiOf(frequency, a4)
        return strings.indices.minBy { abs(strings[it] - midi) }
    }

    /** Cents between [frequency] and the open note of [string]. */
    fun centsOff(string: Int, frequency: Double, a4: Double = 440.0): Double =
        (Notes.midiOf(frequency, a4) - strings[string]) * 100

    companion object {
        private fun of(id: String, notes: String) = Tuning(id, notes.split(" ").map { Notes.parse(it)!! })

        val STANDARD = of("standard", "E2 A2 D3 G3 B3 E4")
        val PRESETS = listOf(
            STANDARD,
            of("half_step_down", "Eb2 Ab2 Db3 Gb3 Bb3 Eb4"),
            of("whole_step_down", "D2 G2 C3 F3 A3 D4"),
            of("drop_d", "D2 A2 D3 G3 B3 E4"),
            of("drop_c", "C2 G2 C3 F3 A3 D4"),
            of("dadgad", "D2 A2 D3 G3 A3 D4"),
            of("open_d", "D2 A2 D3 F#3 A3 D4"),
            of("open_g", "D2 G2 D3 G3 B3 D4"),
            of("open_e", "E2 B2 E3 G#3 B3 E4"),
        )

        fun byId(id: String): Tuning? = PRESETS.firstOrNull { it.id == id }
    }
}
