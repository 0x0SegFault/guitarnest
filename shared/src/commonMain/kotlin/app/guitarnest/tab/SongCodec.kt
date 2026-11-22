package app.guitarnest.tab

import kotlinx.serialization.json.Json

/** Reads and writes .gnt files, and checks their content. */
object SongCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    fun encode(song: Song): String = json.encodeToString(Song.serializer(), song.copy(format = FORMAT_VERSION))

    /** Returns the song, or throws [TabFormatException] with a short reason. */
    fun decode(text: String): Song {
        val song = try {
            json.decodeFromString(Song.serializer(), text)
        } catch (e: IllegalArgumentException) {
            throw TabFormatException("Not a GuitarNest tab: ${e.message?.lineSequence()?.firstOrNull()}")
        }
        if (song.format > FORMAT_VERSION) throw TabFormatException("Made with a newer GuitarNest (format ${song.format})")
        val problems = validate(song)
        if (problems.isNotEmpty()) throw TabFormatException(problems.first())
        return song
    }

    fun validate(song: Song): List<String> = buildList {
        if (song.title.isBlank()) add("The title is empty")
        if (song.tuningNotes() == null) add("Unknown tuning: ${song.tuning}")
        if (song.capo !in 0..12) add("Capo must be 0 to 12")
        if (song.tempo !in 20..300) add("Tempo must be 20 to 300 BPM")
        if (song.beatsPerBar !in 1..12) add("Beats per bar must be 1 to 12")
        song.patterns.forEach { p ->
            if (p.perBeat !in 1..4) add("Pattern ${p.id}: 1 to 4 steps per beat")
            if (p.steps.any { it !in "DUx-" }) add("Pattern ${p.id}: use only D, U, x and -")
        }
        val ids = song.patterns.map { it.id }.toSet()
        song.sections.forEach { s ->
            if (s.strum != null && s.strum !in ids) add("Section ${s.name}: unknown pattern ${s.strum}")
            s.bars.forEach { bar ->
                if (bar.strum != null && bar.strum !in ids) add("Section ${s.name}: unknown pattern ${bar.strum}")
                bar.beats.forEach { beat ->
                    if (beat.at < 0 || beat.at >= song.beatsPerBar) add("Section ${s.name}: a note is outside its bar")
                    beat.notes.forEach { n ->
                        if (n.string !in 1..6) add("Section ${s.name}: string ${n.string} does not exist")
                        if (n.fret !in 0..24) add("Section ${s.name}: fret ${n.fret} is out of range")
                    }
                }
            }
        }
    }
}

class TabFormatException(message: String) : Exception(message)
