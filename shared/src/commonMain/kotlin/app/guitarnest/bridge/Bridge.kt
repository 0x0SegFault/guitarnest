package app.guitarnest.bridge

import app.guitarnest.about.CreditSection
import app.guitarnest.about.Credits
import app.guitarnest.ai.AiTabMaker
import app.guitarnest.ai.AiTask
import app.guitarnest.ai.ApiStyle
import app.guitarnest.ai.ChatMessage
import app.guitarnest.ai.ChatRequest
import app.guitarnest.ai.SongInfo
import app.guitarnest.ai.SongSearch
import app.guitarnest.data.BackupContent
import app.guitarnest.data.PracticeEntry
import app.guitarnest.listen.AcoustId
import app.guitarnest.listen.Arranger
import app.guitarnest.listen.BasicPitch
import app.guitarnest.practice.ClickTrack
import app.guitarnest.practice.PRESET_PATTERNS
import app.guitarnest.practice.SpeedTrainer
import app.guitarnest.report.Reports
import app.guitarnest.tab.ChordShape
import app.guitarnest.tab.ChordShapes
import app.guitarnest.tab.ExampleSong
import app.guitarnest.tab.PlayAlong
import app.guitarnest.tab.PlayedBar
import app.guitarnest.tab.Level
import app.guitarnest.tab.MidiImport
import app.guitarnest.tab.MusicXmlImport
import app.guitarnest.tab.Section
import app.guitarnest.tab.Song
import app.guitarnest.tab.SongCodec
import app.guitarnest.tab.StrumPattern
import app.guitarnest.tab.TextTab
import app.guitarnest.tuner.NoteReading
import app.guitarnest.tuner.Notes
import app.guitarnest.tuner.PitchDetector
import app.guitarnest.tuner.ToneSynth
import app.guitarnest.tuner.Tuning
import app.guitarnest.tuner.TunerTracker
import app.guitarnest.tuner.readingOf

/** A section while it is edited: its bars are text (see [TextTab]). */
data class SectionDraft(val name: String, val repeat: Int, val strum: String?, val text: String)

/** The result of an edit: a valid song, or the first problem. */
data class BuildResult(val song: Song?, val error: String?)

/**
 * Entry points that are simple to call from Swift (no default arguments, no generics),
 * and the editor rules shared by Android and iOS.
 */
object Bridge {
    // Not "newSong": Objective-C reserves names that start with "new", "init", "copy", "alloc".
    fun createSong(title: String): Song = Song(title = title)

    fun level(index: Int): Level = Level.entries[index.coerceIn(0, Level.entries.size - 1)]

    fun levelIndex(level: Level): Int = level.ordinal

    fun pattern(id: String, name: String, steps: String, perBeat: Int): StrumPattern = StrumPattern(id, name, steps, perBeat)


    // "repeat" is a Swift keyword: Swift reads and makes it through these.
    fun sectionRepeat(section: Section): Int = section.repeat
    fun draftRepeat(draft: SectionDraft): Int = draft.repeat
    fun draft(name: String, repeats: Int, strum: String?, text: String): SectionDraft = SectionDraft(name, repeats, strum, text)

    fun drafts(song: Song): List<SectionDraft> =
        song.sections.map { SectionDraft(it.name, it.repeat, it.strum, TextTab.sectionText(it, song.beatsPerBar)) }

    /** Checks one section text; null when it is fine. */
    fun sectionProblem(text: String, beatsPerBar: Int): String? = TextTab.parseSection(text, beatsPerBar).exceptionOrNull()?.message

    fun build(
        base: Song, title: String, artist: String, source: String, level: Level, tuning: String,
        capo: Int, tempo: Int, beatsPerBar: Int, patterns: List<StrumPattern>, sections: List<SectionDraft>, notes: String,
    ): BuildResult {
        val built = sections.map { d ->
            val bars = TextTab.parseSection(d.text, beatsPerBar).getOrElse { return BuildResult(null, "${d.name}: ${it.message}") }
            Section(d.name.ifBlank { "Section" }, bars, d.repeat.coerceIn(1, 16), d.strum)
        }
        val song = base.copy(
            title = title, artist = artist, source = source, level = level, tuning = tuning, capo = capo, tempo = tempo,
            beatsPerBar = beatsPerBar, patterns = patterns, sections = built, notes = notes,
        )
        val problems = SongCodec.validate(song)
        return if (problems.isEmpty()) BuildResult(song, null) else BuildResult(null, problems.first())
    }

    /** A file's bytes: MIDI, or a .gnt / MusicXML text. */
    @Throws(Exception::class)
    fun importBytes(bytes: ByteArray, name: String, source: String): Song =
        if (MidiImport.isMidi(bytes)) MidiImport.import(bytes, name, source) else importText(bytes.decodeToString(), source)

    /** A .gnt or MusicXML text (compressed .mxl files are unpacked by the platform first). */
    @Throws(Exception::class)
    fun importText(text: String, source: String): Song =
        if (text.trimStart().startsWith("<")) MusicXmlImport.import(text, source) else SongCodec.decode(text)

    fun printText(song: Song): String = TextTab.printLines(song).joinToString("\n")

    fun barLines(song: Song, section: Int, bar: Int): String = TextTab.barLines(song.sections[section].bars[bar], song.beatsPerBar).joinToString("\n")

    fun chordCells(song: Song, section: Int, bar: Int): List<String> = TextTab.chordCells(song.sections[section].bars[bar], song.beatsPerBar)

    // Everything below wraps shared code for Swift. @Throws turns Kotlin exceptions into Swift errors
    // (without it an exception would stop the iOS app).

    fun tuningPresets(): List<Tuning> = Tuning.PRESETS
    fun tuningById(id: String): Tuning? = Tuning.byId(id)
    fun exampleSong(): Song = ExampleSong.song
    fun presetPatterns(): List<StrumPattern> = PRESET_PATTERNS
    fun encodeSong(song: Song): String = SongCodec.encode(song)
    fun validate(song: Song): List<String> = SongCodec.validate(song)
    fun withId(song: Song, id: String): Song = song.copy(id = id)

    @Throws(Exception::class)
    fun decodeSong(text: String): Song = SongCodec.decode(text)

    fun pitchDetector(sampleRate: Int): PitchDetector = PitchDetector(sampleRate)
    fun tunerTracker(): TunerTracker = TunerTracker()
    fun noteReading(frequency: Double, a4: Double): NoteReading = readingOf(frequency, a4)
    fun noteName(midi: Int, flats: Boolean): String = Notes.name(midi, flats)

    fun clickTrack(bpm: Double, beatsPerBar: Int, subdivisions: Int, sampleRate: Int): ClickTrack = ClickTrack(bpm, beatsPerBar, subdivisions, sampleRate)
    fun speedTrainer(start: Int, target: Int, step: Int, barsPerStep: Int): SpeedTrainer = SpeedTrainer(start, target, step, barsPerStep)

    fun practiceEntry(songId: String, day: Long, seconds: Int, bestTempo: Int): PracticeEntry = PracticeEntry(songId, day, seconds, bestTempo)
    fun backupEncode(songs: List<Song>, practice: List<PracticeEntry>): String = BackupContent.encode(BackupContent(songs = songs, practice = practice))
    @Throws(Exception::class)
    fun backupDecode(text: String): BackupContent = BackupContent.decode(text)
    fun mergePractice(current: List<PracticeEntry>, incoming: List<PracticeEntry>): List<PracticeEntry> = BackupContent.merge(current, incoming)
    fun encodePractice(entries: List<PracticeEntry>): String = BackupContent.encode(BackupContent(practice = entries))

    fun aiTask(index: Int): AiTask = AiTask.entries[index]
    fun aiRequest(model: String, task: AiTask, level: Level, request: String, song: Song?): ChatRequest = AiTabMaker.request(model, task, level, request, song)
    fun aiTestRequest(model: String): ChatRequest = ChatRequest(model, listOf(ChatMessage("user", "Answer with the word OK.")))
    fun aiPath(baseUrl: String): String = AiTabMaker.path(AiTabMaker.styleFor(baseUrl))
    fun aiIsAnthropic(baseUrl: String): Boolean = AiTabMaker.styleFor(baseUrl) == ApiStyle.ANTHROPIC
    fun aiBody(request: ChatRequest, baseUrl: String): String = AiTabMaker.encode(request, AiTabMaker.styleFor(baseUrl))
    @Throws(Exception::class)
    fun aiAnswer(body: String, baseUrl: String): String = AiTabMaker.answerText(body, AiTabMaker.styleFor(baseUrl))
    @Throws(Exception::class)
    fun aiSong(answer: String, source: String, level: Level?): Song = AiTabMaker.songFrom(answer, source, level)
    fun aiRepair(request: ChatRequest, answer: String, problem: String): ChatRequest = AiTabMaker.repair(request, answer, problem)

    fun searchUrl(query: String): String = SongSearch.url(query)
    fun searchUserAgent(): String = SongSearch.USER_AGENT
    @Throws(Exception::class)
    fun searchParse(body: String): List<SongInfo> = SongSearch.parse(body)

    @Throws(Exception::class)
    fun basicPitch(model: ByteArray): BasicPitch = BasicPitch(model)

    // Chord diagrams and play along
    fun chordsInSong(song: Song): List<String> = PlayAlong.chordsInSong(song)
    fun shapeFor(song: Song, chord: String): ChordShape? = ChordShapes.shapeFor(song, chord)
    fun nextShape(song: Song, chord: String): Song = PlayAlong.nextShape(song, chord)
    fun chordAt(order: List<PlayedBar>, index: Int, beat: Int): String? = PlayAlong.chordAt(order, index, beat)
    fun nextChord(order: List<PlayedBar>, index: Int, beat: Int): String? = PlayAlong.nextChord(order, index, beat)
    /** String index to pick (0 = low E), or -1 for none. */
    fun pickString(pattern: StrumPattern, step: Int, shape: ChordShape?): Int = pattern.pickString(step, shape) ?: -1
    fun shapeFrets(shape: ChordShape): List<Int> = shape.frets
    fun shapeFirstFret(shape: ChordShape): Int = shape.firstFret
    /** Barre as [fret, from string, to string], or empty. */
    fun shapeBarre(shape: ChordShape): List<Int> = shape.barre?.let { listOf(it.first, it.second, it.third) } ?: emptyList()
    fun shapeText(shape: ChordShape): String = shape.toString()
    fun patternKind(pattern: StrumPattern): String = pattern.kind
    fun patternBeats(pattern: StrumPattern): Int = pattern.beats
    fun pattern(id: String, name: String, steps: String, perBeat: Int, kind: String): StrumPattern = StrumPattern(id, name, steps, perBeat, kind)

    // Tuner reference sounds
    fun pluck(frequency: Double, sampleRate: Int): FloatArray = ToneSynth.pluck(frequency, sampleRate)
    fun fork(frequency: Double, sampleRate: Int): FloatArray = ToneSynth.fork(frequency, sampleRate)
    fun frequencyOf(midi: Int, a4: Double): Double = Notes.frequencyOf(midi, a4)

    // Credits
    fun creditSections(): List<CreditSection> = Credits.SECTIONS

    // Song identification (AcoustID)
    fun acoustIdAvailable(): Boolean = AcoustId.available
    fun acoustIdUrl(): String = AcoustId.URL
    /** Form body to POST, or null when the audio is shorter than 10 s. */
    fun acoustIdForm(audio: FloatArray, sampleRate: Int): String? =
        AcoustId.fingerprint(audio, sampleRate)?.let { (fp, seconds) -> AcoustId.form(fp, seconds) }
    @Throws(IllegalStateException::class, kotlinx.serialization.SerializationException::class)
    fun acoustIdParse(body: String): List<SongInfo> = AcoustId.parse(body)
    fun named(song: Song, title: String, artist: String): Song = song.copy(title = title, artist = artist)

    // Reports
    fun reportServiceUrl(): String? = Reports.SERVICE_URL
    @Throws(Exception::class)
    fun reportChallenge(body: String): Reports.Challenge = Reports.parseChallenge(body)
    fun reportSolve(challenge: Reports.Challenge): Reports.Proof? = Reports.solve(challenge)
    fun reportBody(kind: String, title: String, details: String, version: String, device: String, proof: Reports.Proof, followUp: String): String =
        Reports.body(kind, title, details, version, device, proof, followUp)
    fun reportPreview(kind: String, title: String, details: String, version: String, device: String): String =
        Reports.preview(kind, title, details, version, device)
    fun reportSent(code: String, kind: String, title: String, day: Long): Reports.Sent = Reports.Sent(code, kind, title, day)
    fun reportEncodeSent(list: List<Reports.Sent>): String = Reports.encodeSent(list)
    fun reportDecodeSent(text: String): List<Reports.Sent> = Reports.decodeSent(text)
    fun reportParseStatus(body: String): Reports.Status = Reports.parseStatus(body)
    // "public" is a Swift keyword: Swift reads the status through these.
    fun reportStatusName(status: Reports.Status): String = status.status
    fun reportStatusLink(status: Reports.Status): String? = status.public
    fun reportGithubUrl(kind: String, title: String, details: String, version: String, device: String): String =
        Reports.githubUrl(kind, title, details, version, device)

    /** Audio (mono, 22050 Hz) to a draft song. [progress] gets (windows done, total). */
    @Throws(Exception::class)
    fun listen(model: BasicPitch, audio: FloatArray, level: Level, title: String, progress: (Int, Int) -> Unit): Song? {
        val notes = model.notes(audio, progress)
        return if (notes.size < 8) null else Arranger.makeSong(notes, level, title.ifBlank { "Recording" })
    }
}
