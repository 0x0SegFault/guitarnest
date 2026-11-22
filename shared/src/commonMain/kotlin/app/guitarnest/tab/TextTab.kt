package app.guitarnest.tab

import kotlin.math.roundToInt

/**
 * Plain-text tabs, as guitarists write them. Used to show, print and edit a section.
 *
 * Section text:
 * ```
 * | G . . . | C . D . |        chord line: one token per beat, "." keeps the chord
 * e|-------0-------|-----------|
 * B|-----1---1-----|-----------|    six string lines, high E first
 * G|---0-------0---|-----------|
 * D|-2-------------|-----------|
 * A|---------------|-----------|
 * E|---------------|-----------|
 * ```
 * Each bar has [STEPS_PER_BEAT] steps per beat; each step is [CELL] characters wide.
 */
object TextTab {
    const val STEPS_PER_BEAT = 4
    const val CELL = 2
    private val STRING_LABELS = listOf("e", "B", "G", "D", "A", "E")

    /** Six lines (high E first) for one bar, without the leading label and with a closing "|". */
    fun barLines(bar: Bar, beatsPerBar: Int): List<String> {
        val steps = beatsPerBar * STEPS_PER_BEAT
        val cells = Array(6) { Array(steps) { "-".repeat(CELL) } }
        for (beat in bar.beats) {
            val step = (beat.at * STEPS_PER_BEAT).roundToInt().coerceIn(0, steps - 1)
            for (n in beat.notes) {
                val text = n.fret.toString() + (n.tech ?: "")
                val row = cells[n.string - 1]
                row[step] = text.padEnd(CELL, '-')
                // A wide note ("12h") takes space from the next step, so the bar keeps its width.
                if (text.length > CELL && step + 1 < steps) row[step + 1] = "-".repeat((2 * CELL - text.length).coerceAtLeast(0))
            }
        }
        return cells.map { row -> "-" + row.joinToString("") + "|" }
    }

    /** Chord tokens per beat for one bar: "G . . . ". */
    fun chordCells(bar: Bar, beatsPerBar: Int): List<String> = List(beatsPerBar) { b ->
        bar.chords.firstOrNull { it.beat.roundToInt() == b }?.name ?: "."
    }

    /** One section as editable text: a chord line (if any chords) and the six string lines (if any notes). */
    fun sectionText(section: Section, beatsPerBar: Int): String {
        val lines = mutableListOf<String>()
        if (section.bars.any { it.chords.isNotEmpty() }) {
            lines += "| " + section.bars.joinToString(" | ") { chordCells(it, beatsPerBar).joinToString(" ") } + " |"
        }
        if (section.bars.any { it.beats.isNotEmpty() }) {
            val bars = section.bars.map { barLines(it, beatsPerBar) }
            for (s in 0 until 6) lines += STRING_LABELS[s] + "|" + bars.joinToString("") { it[s] }
        }
        return lines.joinToString("\n")
    }

    /** Parses section text back into bars. Returns the bars or a list of problems. */
    fun parseSection(text: String, beatsPerBar: Int): Result<List<Bar>> {
        val lines = text.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
        val chordBars = mutableListOf<List<ChordMark>>()
        val noteBars = mutableListOf<List<Beat>>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (isStringLine(line)) {
                val block = lines.subList(i, minOf(i + 6, lines.size)).map { it.trim() }
                if (block.size < 6 || !block.all(::isStringLine)) return fail("A tab needs six string lines together")
                noteBars += parseNoteBlock(block, beatsPerBar).getOrElse { return Result.failure(it) }
                i += 6
            } else if (line.startsWith("|") || line.first().isLetter()) {
                chordBars += parseChordLine(line, beatsPerBar).getOrElse { return Result.failure(it) }
                i++
            } else {
                return fail("Cannot read this line: $line")
            }
        }
        val count = maxOf(chordBars.size, noteBars.size)
        return Result.success(List(count) { Bar(chords = chordBars.getOrElse(it) { emptyList() }, beats = noteBars.getOrElse(it) { emptyList() }) })
    }

    private fun isStringLine(line: String) = line.length > 2 && line[0] in "eBGDAEbgdaE" && line[1] == '|'

    private fun parseChordLine(line: String, beatsPerBar: Int): Result<List<List<ChordMark>>> {
        val bars = line.trim().trim('|').split("|").map { it.trim() }.filter { it.isNotEmpty() }
        return Result.success(bars.map { bar ->
            val tokens = bar.split(Regex("\\s+"))
            if (tokens.size == 1 && tokens[0] != ".") listOf(ChordMark(0.0, tokens[0]))
            else tokens.take(beatsPerBar).mapIndexedNotNull { b, t -> if (t == ".") null else ChordMark(b.toDouble(), t) }
        })
    }

    private fun parseNoteBlock(block: List<String>, beatsPerBar: Int): Result<List<List<Beat>>> {
        // Cut each string line into bars at "|", then place notes by their position in the bar.
        val perString = block.map { it.substring(2).trimEnd('|').split("|") }
        val barCount = perString.maxOf { it.size }
        val result = mutableListOf<List<Beat>>()
        for (b in 0 until barCount) {
            val notesAt = mutableMapOf<Double, MutableList<TabNote>>()
            for (s in 0 until 6) {
                val content = perString[s].getOrElse(b) { "" }
                if (content.isEmpty()) continue
                var c = 0
                while (c < content.length) {
                    if (content[c].isDigit()) {
                        val start = c
                        while (c < content.length && content[c].isDigit()) c++
                        val fret = content.substring(start, c).toInt()
                        val tech = content.getOrNull(c)?.takeIf { it in "hp/\\b~x" }?.toString()
                        if (tech != null) c++
                        val steps = beatsPerBar * STEPS_PER_BEAT
                        // Our own grid ("-" + CELL characters per step) is exact; hand-written tabs
                        // are placed by their position in the bar.
                        val step = if (content.length == 1 + steps * CELL) (start - 1) / CELL
                        else (start.toDouble() / content.length * steps).roundToInt().coerceIn(0, steps - 1)
                        val at = step.toDouble() / STEPS_PER_BEAT
                        notesAt.getOrPut(at) { mutableListOf() } += TabNote(s + 1, fret, tech)
                    } else {
                        c++
                    }
                }
            }
            result += notesAt.entries.sortedBy { it.key }.map { (at, notes) -> Beat(at, notes.sortedBy { it.string }) }
        }
        return Result.success(result)
    }

    private fun <T> fail(message: String): Result<T> = Result.failure(TabFormatException(message))

    /** The whole song as printable text lines, wrapped to [width] characters. */
    fun printLines(song: Song, width: Int = 80): List<String> = buildList {
        add(song.title + if (song.artist.isNotBlank()) " – ${song.artist}" else "")
        val tuning = song.tuningNotes()?.names?.joinToString(" ") { it.dropLast(1) } ?: song.tuning
        add("Tuning: $tuning   Capo: ${if (song.capo == 0) "none" else song.capo}   Tempo: ${song.tempo} BPM   ${song.beatsPerBar}/4")
        if (song.source.isNotBlank()) add("Tab: ${song.source}")
        song.patterns.forEach { add("Strumming ${it.name}: ${strumText(it)}") }
        add("")
        val barWidth = 1 + song.beatsPerBar * STEPS_PER_BEAT * CELL + 1
        val barsPerLine = ((width - 2) / barWidth).coerceAtLeast(1)
        for (section in song.sections) {
            add("[${section.name}]" + (if (section.repeat > 1) " x${section.repeat}" else "") +
                (song.pattern(section.strum)?.let { "  strum: ${it.name}" } ?: ""))
            section.bars.chunked(barsPerLine).forEach { chunk ->
                if (chunk.any { it.chords.isNotEmpty() }) {
                    add("  " + chunk.joinToString("") { bar ->
                        val cells = chordCells(bar, song.beatsPerBar)
                        cells.joinToString("") { (if (it == ".") "" else it).padEnd(STEPS_PER_BEAT * CELL) }.padEnd(barWidth)
                    }.trimEnd())
                }
                if (chunk.any { it.beats.isNotEmpty() }) {
                    val lines = chunk.map { barLines(it, song.beatsPerBar) }
                    for (s in 0 until 6) add(STRING_LABELS[s] + "|" + lines.joinToString("") { it[s] })
                }
                add("")
            }
        }
        if (song.notes.isNotBlank()) { add(song.notes); add("") }
        add("For personal practice only. Made with GuitarNest.")
    }

    /** "D - D U - U D U" with arrows for display. */
    fun strumText(pattern: StrumPattern): String = pattern.steps.map {
        when (it) { 'D' -> "↓"; 'U' -> "↑"; 'x' -> "×"; else -> "·" }
    }.joinToString(" ")
}
