package app.guitarnest.tuner

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/** Equal temperament with a configurable reference (A4 = 440 Hz by default). */
object Notes {
    val NAMES = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    val FLAT_NAMES = listOf("C", "D♭", "D", "E♭", "E", "F", "G♭", "G", "A♭", "A", "B♭", "B")

    /** MIDI note as a real number: 69.0 is A4, 69.5 is a quarter tone above. */
    fun midiOf(frequency: Double, a4: Double = 440.0): Double = 69 + 12 * log2(frequency / a4)

    fun frequencyOf(midi: Int, a4: Double = 440.0): Double = a4 * 2.0.pow((midi - 69) / 12.0)

    /** Note name with octave, for example "E2" for MIDI 40. */
    fun name(midi: Int, flats: Boolean = false): String =
        (if (flats) FLAT_NAMES else NAMES)[midi.mod(12)] + (midi / 12 - 1)

    /** Parses "E2", "F#3", "Bb1" or "C♯4". Returns null for invalid text. */
    fun parse(text: String): Int? {
        val match = Regex("^([A-Ga-g])([#♯b♭]?)(-?\\d)$").matchEntire(text.trim()) ?: return null
        val (letter, accidental, octave) = match.destructured
        val base = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)
            .getValue(letter.uppercase()[0])
        val shift = when (accidental) { "#", "♯" -> 1; "b", "♭" -> -1; else -> 0 }
        return (octave.toInt() + 1) * 12 + base + shift
    }
}

/** The nearest note to a frequency and how far off it is. */
data class NoteReading(val midi: Int, val cents: Double) {
    val name: String get() = Notes.name(midi)
}

fun readingOf(frequency: Double, a4: Double = 440.0): NoteReading {
    val exact = Notes.midiOf(frequency, a4)
    val midi = exact.roundToInt()
    return NoteReading(midi, (exact - midi) * 100)
}
