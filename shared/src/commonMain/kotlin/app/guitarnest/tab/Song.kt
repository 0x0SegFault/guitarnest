package app.guitarnest.tab

import app.guitarnest.tuner.Notes
import app.guitarnest.tuner.Tuning
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val FORMAT_VERSION = 1

@Serializable
enum class Level {
    @SerialName("beginner") BEGINNER,
    @SerialName("intermediate") INTERMEDIATE,
    @SerialName("advanced") ADVANCED,
}

/**
 * A song in the GuitarNest tab format (.gnt, JSON). See docs/TAB_FORMAT.md.
 * Strings are numbered as in tab notation: 1 = high E, 6 = low E.
 * Frets are counted from the capo.
 */
@Serializable
data class Song(
    val format: Int = FORMAT_VERSION,
    val id: String = "",
    val title: String,
    val artist: String = "",
    /** Who made this tab, for example "Written by me" or "AI, from my recording". */
    val source: String = "",
    val level: Level = Level.INTERMEDIATE,
    /** A tuning preset id ("standard", "drop_d"...) or six notes, low to high: "D2 A2 D3 G3 B3 E4". */
    val tuning: String = "standard",
    val capo: Int = 0,
    val tempo: Int = 90,
    val beatsPerBar: Int = 4,
    val patterns: List<StrumPattern> = emptyList(),
    val sections: List<Section> = emptyList(),
    val notes: String = "",
) {
    fun tuningNotes(): Tuning? = Tuning.byId(tuning) ?: run {
        val parts = tuning.trim().split(Regex("\\s+"))
        val midi = parts.map { Notes.parse(it) ?: return null }
        if (midi.size != 6) null else Tuning("custom", midi, flats = tuning.contains('b'))
    }

    fun pattern(id: String?): StrumPattern? = patterns.firstOrNull { it.id == id }

    /** Bars in playing order, with repeats expanded. */
    fun playOrder(): List<PlayedBar> = buildList {
        sections.forEachIndexed { s, section ->
            repeat(section.repeat.coerceAtLeast(1)) { pass ->
                section.bars.forEachIndexed { b, bar -> add(PlayedBar(s, b, pass, bar, section.strum)) }
            }
        }
    }
}

data class PlayedBar(val section: Int, val bar: Int, val pass: Int, val content: Bar, val sectionStrum: String?) {
    val strum: String? get() = content.strum ?: sectionStrum
}

/**
 * A strumming pattern. [steps] has one character per subdivision:
 * D = down, U = up, x = muted strum, - = no stroke (hand still moves).
 */
@Serializable
data class StrumPattern(
    val id: String,
    val name: String = id,
    val steps: String,
    /** Subdivisions per beat: 2 = eighth notes, 4 = sixteenth notes. */
    val perBeat: Int = 2,
) {
    /** Stroke at a subdivision; the pattern repeats over the bar. */
    fun strokeAt(step: Int): Char = if (steps.isEmpty()) '-' else steps[step.mod(steps.length)]

    /** The hand goes down on the beat and up between beats, also when it does not hit the strings. */
    fun handDown(step: Int): Boolean = step % 2 == 0
}

@Serializable
data class Section(
    val name: String,
    val bars: List<Bar> = emptyList(),
    val repeat: Int = 1,
    /** Strumming pattern id for the bars of this section. */
    val strum: String? = null,
)

@Serializable
data class Bar(
    val chords: List<ChordMark> = emptyList(),
    val beats: List<Beat> = emptyList(),
    /** Strumming pattern id for this bar only. */
    val strum: String? = null,
)

/** A chord name that starts at [beat] (0 = first beat of the bar). */
@Serializable
data class ChordMark(val beat: Double = 0.0, val name: String)

/** Notes played together at [at] beats from the start of the bar. */
@Serializable
data class Beat(val at: Double, val notes: List<TabNote>)

@Serializable
data class TabNote(
    /** 1 = high E ... 6 = low E. */
    val string: Int,
    val fret: Int,
    /** Optional technique: h (hammer-on), p (pull-off), / (slide up), \ (slide down), b (bend), ~ (vibrato), x (muted). */
    val tech: String? = null,
)
