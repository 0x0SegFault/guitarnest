package app.guitarnest.listen

import app.guitarnest.tab.Bar
import app.guitarnest.tab.Beat
import app.guitarnest.tab.ChordMark
import app.guitarnest.tab.Level
import app.guitarnest.tab.Section
import app.guitarnest.tab.Song
import app.guitarnest.tab.StrumPattern
import app.guitarnest.tab.TabNote
import kotlin.math.roundToInt

/** From heard notes to a draft song for a level. Port of ai/gntab/arrange.py. */
object Arranger {
    val EASY_SHAPES = setOf("C", "A", "G", "E", "D", "Am", "Em", "Dm", "E7", "A7", "D7", "G7", "C7", "B7", "Am7", "Em7", "Dm7")
    private val FLATS = listOf("C", "Db", "D", "D#", "E", "F", "Gb", "G", "G#", "A", "A#", "B")

    fun transpose(chord: String, semitones: Int): String {
        val rootLength = if (chord.length > 1 && chord[1] in "#b") 2 else 1
        val name = chord.substring(0, rootLength)
        val index = Analysis.NAMES.indexOf(name).takeIf { it >= 0 } ?: FLATS.indexOf(name)
        if (index < 0) return chord
        return Analysis.NAMES[(index + semitones).mod(12)] + chord.substring(rootLength)
    }

    /** The capo (0-7) that turns the most chords into easy open shapes; ties go to the lower capo. */
    fun capoForOpenShapes(chords: List<String>): Int {
        if (chords.isEmpty()) return 0
        return (0..7).maxBy { k -> chords.count { transpose(it, -k) in EASY_SHAPES } * 100 - k }
    }

    /** Phrases of 4 bars; equal phrases get the same name; repeats in a row are merged. */
    fun sections(bars: List<Bar>, strum: String, phrase: Int = 4): List<Section> {
        val names = mutableMapOf<List<List<String>>, String>()
        val out = mutableListOf<Section>()
        for (p in bars.chunked(phrase)) {
            val key = p.map { b -> b.chords.map { it.name } }
            val name = names.getOrPut(key) { "Part " + ('A' + names.size) }
            val last = out.lastOrNull()
            if (last != null && last.name == name && last.bars == p) out[out.size - 1] = last.copy(repeat = last.repeat + 1)
            else out += Section(name, p, 1, strum)
        }
        return out
    }

    fun makeSong(notes: List<HeardNote>, level: Level, title: String, artist: String = "", source: String = "Made by GuitarNest from a recording"): Song {
        require(notes.size >= 8) { "Too few notes" }
        val bpm = Analysis.estimateTempo(notes)
        var grid = Analysis.beatGrid(notes, bpm)
        val end = notes.maxOf { it.time + it.duration }
        val totalBeats = ((end - grid.firstBeat) / grid.beat).toInt() + 1
        var perBeat = Analysis.chordsPerBeat(notes, grid, totalBeats)
        val shift = Analysis.alignDownbeat(perBeat, grid.beatsPerBar)
        grid = grid.copy(firstBeat = grid.beatTime(shift))
        perBeat = perBeat.drop(shift)
        val barCount = (perBeat.size + grid.beatsPerBar - 1) / grid.beatsPerBar

        val setup = Fingering.bestSetup(notes)
        val patterns = mutableMapOf<String, Int>()
        val rawBars = (0 until barCount).map { b ->
            val beats = perBeat.subList(b * grid.beatsPerBar, minOf(perBeat.size, (b + 1) * grid.beatsPerBar))
            var chords = mutableListOf<ChordMark>()
            var last: String? = null
            beats.forEachIndexed { i, c -> if (c != null && c != last) { chords += ChordMark(i.toDouble(), c); last = c } }
            if (level == Level.BEGINNER) {
                chords = beats.filterNotNull().groupingBy { it }.eachCount().maxByOrNull { it.value }?.let { mutableListOf(ChordMark(0.0, it.key)) } ?: mutableListOf()
            }
            val steps = Analysis.strumSteps(notes, grid, b)
            if (steps.any { it != '-' }) patterns[steps] = (patterns[steps] ?: 0) + 1
            val tabBeats = if (level != Level.ADVANCED) emptyList() else {
                val start = grid.beatTime(b * grid.beatsPerBar)
                setup.placed.mapNotNull { p ->
                    val at = ((p.note.time - start) / grid.beat * 4).roundToInt() / 4.0
                    if (at < 0 || at >= grid.beatsPerBar) null else at to TabNote(6 - p.string, p.fret)
                }.groupBy({ it.first }, { it.second }).entries.sortedBy { it.key }.map { (at, ns) -> Beat(at, ns.distinctBy { it.string }.sortedBy { it.string }) }
            }
            Bar(chords = chords, beats = tabBeats)
        }

        // Chord names as they sound; with a capo, show the shapes the player fingers.
        val sounding = rawBars.flatMap { b -> b.chords.map { it.name } }
        val capo = if (level == Level.BEGINNER && setup.tuning.id == "standard") capoForOpenShapes(sounding) else setup.capo
        val bars = rawBars.map { b -> b.copy(chords = b.chords.map { it.copy(name = transpose(it.name, -capo)) }) }

        var main = patterns.maxByOrNull { it.value }?.key ?: "D-D-D-D-"
        if (level == Level.BEGINNER) main = if (main.count { it == 'U' } >= 2) "D-DU-UDU" else "D-D-D-D-"
        return Song(
            title = title, artist = artist, source = source, level = level,
            tuning = setup.tuning.id, capo = capo, tempo = bpm.roundToInt().coerceIn(20, 300), beatsPerBar = grid.beatsPerBar,
            patterns = listOf(StrumPattern("main", "Main", main)),
            sections = sections(bars, "main"),
            notes = "Made from a recording. Tempo, chords, capo and strumming are estimates: check them by ear.",
        )
    }
}
