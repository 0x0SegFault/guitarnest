package app.guitarnest.tab

import app.guitarnest.tuner.Notes
import app.guitarnest.tuner.Tuning

/**
 * MusicXML (partwise) to a GuitarNest song. Reads the guitar part: tab string and fret when present,
 * else pitches placed on the lowest-fret string. Also reads chord symbols, tempo, time, tuning, capo
 * and rehearsal marks (as sections). Guitar Pro files can be exported to MusicXML with TuxGuitar or MuseScore.
 */
object MusicXmlImport {
    private val STEPS = mapOf("C" to 0, "D" to 2, "E" to 4, "F" to 5, "G" to 7, "A" to 9, "B" to 11)
    private val KINDS = mapOf(
        "major" to "", "minor" to "m", "dominant" to "7", "major-seventh" to "maj7", "minor-seventh" to "m7",
        "diminished" to "dim", "augmented" to "aug", "suspended-second" to "sus2", "suspended-fourth" to "sus4",
        "power" to "5", "major-sixth" to "6", "minor-sixth" to "m6", "dominant-ninth" to "9", "half-diminished" to "m7b5",
        "diminished-seventh" to "dim7", "major-ninth" to "maj9", "minor-ninth" to "m9", "dominant-11th" to "11",
    )

    fun import(xml: String, source: String = ""): Song {
        val root = Xml.parse(xml)
        if (root.name != "score-partwise") throw TabFormatException("Only MusicXML score-partwise files are supported")
        val parts = root.all("part")
        if (parts.isEmpty()) throw TabFormatException("No part in this file")
        val part = parts.firstOrNull { p -> p.all("measure").any { m -> m.all("note").any { it.path("notations", "technical", "fret") != null } } }
            ?: parts.first()

        var divisions = 1
        var beats = 4
        var beatType = 4
        var tempo: Int? = null
        var capo = 0
        var tuning: List<Int> = Tuning.STANDARD.strings
        val sections = mutableListOf<Section>()
        var sectionName: String? = null
        var bars = mutableListOf<Bar>()

        fun closeSection() {
            if (bars.isNotEmpty()) sections += Section(sectionName ?: "Part ${sections.size + 1}", bars.toList())
            bars = mutableListOf()
        }

        for (measure in part.all("measure")) {
            var position = 0 // in divisions
            var lastOnset = 0
            val notesAt = mutableMapOf<Double, MutableList<TabNote>>()
            val chords = mutableListOf<ChordMark>()
            for (el in measure.children) {
                when (el.name) {
                    "attributes" -> {
                        el.textOf("divisions")?.toIntOrNull()?.let { divisions = it }
                        el.child("time")?.let { t ->
                            beats = t.textOf("beats")?.substringBefore('+')?.toIntOrNull() ?: beats
                            beatType = t.textOf("beat-type")?.toIntOrNull() ?: beatType
                        }
                        el.path("staff-details")?.let { sd ->
                            sd.textOf("capo")?.toIntOrNull()?.let { capo = it }
                            val lines = sd.all("staff-tuning").sortedBy { it.attributes["line"]?.toIntOrNull() ?: 0 }
                            if (lines.size == 6) tuning = lines.map { pitch(it.textOf("tuning-step"), it.textOf("tuning-alter"), it.textOf("tuning-octave")) ?: 0 }
                        }
                    }
                    "direction" -> {
                        el.path("sound")?.attributes?.get("tempo")?.toDoubleOrNull()?.let { tempo = tempo ?: it.toInt() }
                        el.path("direction-type", "metronome")?.textOf("per-minute")?.toDoubleOrNull()?.let { tempo = tempo ?: it.toInt() }
                        el.path("direction-type", "rehearsal")?.text?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
                            closeSection(); sectionName = name
                        }
                    }
                    "sound" -> el.attributes["tempo"]?.toDoubleOrNull()?.let { tempo = tempo ?: it.toInt() }
                    "harmony" -> {
                        val step = el.textOf("root", "root-step")
                        if (step != null) {
                            val alter = el.textOf("root", "root-alter")?.toDoubleOrNull()?.toInt() ?: 0
                            val kind = el.child("kind")?.let { k -> k.attributes["text"] ?: KINDS[k.text.trim()] } ?: ""
                            val bass = el.textOf("bass", "bass-step")?.let { b -> "/" + b + accidental(el.textOf("bass", "bass-alter")?.toDoubleOrNull()?.toInt() ?: 0) } ?: ""
                            val offset = el.textOf("offset")?.toIntOrNull() ?: 0
                            chords += ChordMark(quarters(position + offset, divisions, beatType), step + accidental(alter) + kind + bass)
                        }
                    }
                    "backup" -> position -= el.textOf("duration")?.toIntOrNull() ?: 0
                    "forward" -> position += el.textOf("duration")?.toIntOrNull() ?: 0
                    "note" -> {
                        val isChord = el.child("chord") != null
                        val grace = el.child("grace") != null
                        val onset = if (isChord) lastOnset else position
                        if (!grace && el.child("rest") == null) {
                            val tech = el.path("notations", "technical")
                            val string = tech?.textOf("string")?.toIntOrNull()
                            val fret = tech?.textOf("fret")?.toIntOrNull()
                            val at = quarters(onset, divisions, beatType)
                            val list = notesAt.getOrPut(at) { mutableListOf() }
                            val note = if (string != null && fret != null) TabNote(string, fret, technique(el))
                            else el.child("pitch")?.let { p ->
                                pitch(p.textOf("step"), p.textOf("alter"), p.textOf("octave"))?.let { place(it, tuning, capo, list.map { n -> n.string }.toSet()) }
                            }
                            if (note != null && note.string in 1..6 && list.none { it.string == note.string }) list += note
                        }
                        if (!isChord) {
                            lastOnset = position
                            if (!grace) position += el.textOf("duration")?.toIntOrNull() ?: 0
                        }
                    }
                }
            }
            val beatsPerBar = beats * 4 / beatType
            bars += Bar(
                chords = chords.filter { it.beat < beatsPerBar }.distinctBy { it.beat },
                beats = notesAt.filter { it.key < beatsPerBar && it.value.isNotEmpty() }.entries.sortedBy { it.key }.map { (at, n) -> Beat(at, n.sortedBy { it.string }) },
            )
        }
        closeSection()

        val title = root.textOf("work", "work-title") ?: root.textOf("movement-title") ?: "Imported song"
        val artist = root.child("identification")?.all("creator")?.firstOrNull { it.attributes["type"] in setOf("composer", "artist") }?.text?.trim() ?: ""
        val tuningText = Tuning.PRESETS.firstOrNull { it.strings == tuning }?.id ?: tuning.joinToString(" ") { Notes.name(it).replace("♯", "#") }
        // Long imports without rehearsal marks are cut into parts of 8 bars, so a part can be looped.
        val split = if (sections.size == 1 && sections[0].bars.size > 16) sections[0].bars.chunked(8).mapIndexed { i, b -> Section("Part ${i + 1}", b) } else sections
        return Song(
            title = title, artist = artist, source = source, tuning = tuningText, capo = capo,
            tempo = (tempo ?: 90).coerceIn(20, 300), beatsPerBar = (beats * 4 / beatType).coerceIn(1, 12), sections = split,
        )
    }

    /** Divisions to quarter-note beats; 6/8 counts in quarters too, so its bar has 3 beats. */
    private fun quarters(divs: Int, divisions: Int, beatType: Int): Double {
        val q = divs.toDouble() / divisions.coerceAtLeast(1)
        return kotlin.math.round(q * 4) / 4 // to the sixteenth-note grid
    }

    private fun pitch(step: String?, alter: String?, octave: String?): Int? {
        val s = STEPS[step?.uppercase()] ?: return null
        val o = octave?.toIntOrNull() ?: return null
        return (o + 1) * 12 + s + (alter?.toDoubleOrNull()?.toInt() ?: 0)
    }

    private fun accidental(alter: Int) = when (alter) { 1 -> "#"; -1 -> "b"; else -> "" }

    /** Lowest fret on a free string; strings are numbered 1 = high E. */
    private fun place(midi: Int, tuning: List<Int>, capo: Int, used: Set<Int>): TabNote? =
        (0 until 6).map { low -> 6 - low to midi - tuning[low] - capo }
            .filter { (string, fret) -> fret in 0..24 && string !in used }
            .minByOrNull { it.second }?.let { (string, fret) -> TabNote(string, fret) }

    private fun technique(note: XmlNode): String? {
        val t = note.path("notations", "technical") ?: return null
        return when {
            t.child("hammer-on")?.attributes?.get("type") == "start" -> "h"
            t.child("pull-off")?.attributes?.get("type") == "start" -> "p"
            t.child("bend") != null -> "b"
            note.path("notations", "slide")?.attributes?.get("type") == "start" -> "/"
            else -> null
        }
    }
}
