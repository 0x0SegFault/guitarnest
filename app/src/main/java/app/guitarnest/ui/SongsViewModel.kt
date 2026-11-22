package app.guitarnest.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.guitarnest.ai.AiTabMaker
import app.guitarnest.ai.AiTask
import app.guitarnest.ai.ChatMessage
import app.guitarnest.ai.ChatRequest
import app.guitarnest.ai.SongInfo
import app.guitarnest.audio.ClickPlayer
import app.guitarnest.R
import app.guitarnest.data.AiConnection
import app.guitarnest.data.BackupContent
import app.guitarnest.data.BackupCrypto
import app.guitarnest.data.NotABackupException
import app.guitarnest.data.WrongPasswordException
import app.guitarnest.data.AiSettings
import app.guitarnest.net.AiClient
import app.guitarnest.net.MusicBrainzClient
import app.guitarnest.net.makeSong
import app.guitarnest.tab.Level
import app.guitarnest.tab.TabFormatException
import app.guitarnest.data.PracticeEntry
import app.guitarnest.data.SongRepository
import app.guitarnest.practice.MAX_BPM
import app.guitarnest.practice.MIN_BPM
import app.guitarnest.practice.SpeedTrainer
import app.guitarnest.practice.TapTempo
import app.guitarnest.tab.PlayedBar
import app.guitarnest.tab.Song
import app.guitarnest.tab.StrumPattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Common strumming patterns (generic rhythms, not from any song). */
val PRESET_PATTERNS = listOf(
    StrumPattern("downs", "Downstrokes", "D-D-D-D-"),
    StrumPattern("folk", "Folk", "D-DU-UDU"),
    StrumPattern("pop", "Pop", "D-DUDUDU"),
    StrumPattern("eighths", "Eighths", "DUDUDUDU"),
    StrumPattern("waltz", "Waltz (3/4)", "D-DUDU"),
    StrumPattern("sixteenths", "Sixteenths", "D-DUD-DUD-DUD-DU", perBeat = 4),
)

data class MetronomeSettings(
    val bpm: Int = 80,
    val beatsPerBar: Int = 4,
    val subdivisions: Int = 1,
    val pattern: StrumPattern? = null,
    val trainer: Boolean = false,
    val trainerTarget: Int = 120,
    val trainerStep: Int = 5,
    val trainerBars: Int = 4,
)

const val STEPS_PER_BEAT = 4

/** Step of [pattern] at a sixteenth-note [step] in the bar. */
fun patternStep(pattern: StrumPattern, step: Int): Int = step * pattern.perBeat / STEPS_PER_BEAT

/** What the player is doing now. [bar] is -1 during the count-in. [step] is in sixteenth notes. */
data class Playing(val songId: String?, val bar: Int, val beat: Int, val step: Int, val bpm: Int)

data class PlayOptions(val tempoPercent: Int = 100, val loopSection: Int? = null, val trainer: Boolean = false, val countIn: Boolean = true)

/** State of a network job (AI or search) for the sheets. */
data class Job<T>(val running: Boolean = false, val result: T? = null, val error: String? = null)

class SongsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SongRepository(app)
    private val aiSettings = AiSettings(app)
    private val _ai = MutableStateFlow(aiSettings.load())
    val ai: StateFlow<AiConnection> = _ai.asStateFlow()
    private val _aiJob = MutableStateFlow(Job<String>())
    val aiJob: StateFlow<Job<String>> = _aiJob.asStateFlow()
    private val _search = MutableStateFlow(Job<List<SongInfo>>())
    val search: StateFlow<Job<List<SongInfo>>> = _search.asStateFlow()
    private val player = ClickPlayer()
    private val tap = TapTempo()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()
    private val _log = MutableStateFlow<List<PracticeEntry>>(emptyList())
    val log: StateFlow<List<PracticeEntry>> = _log.asStateFlow()
    private val _metronome = MutableStateFlow(MetronomeSettings())
    val metronome: StateFlow<MetronomeSettings> = _metronome.asStateFlow()
    private val _playing = MutableStateFlow<Playing?>(null)
    val playing: StateFlow<Playing?> = _playing.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var sessionStart = 0L
    private var bestTempo = 0

    init {
        player.onEnd = { viewModelScope.launch { stop() } }
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        val (songs, log) = withContext(Dispatchers.IO) {
            if (repo.all().isEmpty() && !repo.seeded()) repo.save(ExampleSong.song).also { repo.markSeeded() }
            repo.all() to repo.practiceLog()
        }
        _songs.value = songs
        _log.value = log
    }

    fun save(song: Song, done: (Song) -> Unit = {}) = viewModelScope.launch {
        val saved = withContext(Dispatchers.IO) { repo.save(song) }
        refresh()
        done(saved)
    }

    fun delete(id: String) = viewModelScope.launch {
        withContext(Dispatchers.IO) { repo.delete(id) }
        refresh()
    }

    fun import(bytes: ByteArray, done: (Song) -> Unit) = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) { runCatching { repo.import(bytes) } }
        result.onSuccess { refresh(); done(it) }.onFailure { _message.value = it.message }
    }

    fun showMessage(text: String?) { _message.value = text }

    // Backups: songs and practice history, encrypted with the player's password.

    fun createBackup(password: CharArray, done: (ByteArray) -> Unit) = viewModelScope.launch {
        val blob = withContext(Dispatchers.Default) {
            BackupCrypto.encrypt(BackupContent.encode(repo.backupContent()).toByteArray(), password).also { password.fill(' ') }
        }
        done(blob)
    }

    /** [done] gets null on success, else the message to show in the sheet. */
    fun restoreBackup(blob: ByteArray, password: CharArray, done: (String?) -> Unit) = viewModelScope.launch {
        val app = getApplication<Application>()
        val result = withContext(Dispatchers.Default) {
            runCatching { repo.restore(BackupContent.decode(BackupCrypto.decrypt(blob, password).decodeToString())) }.also { password.fill(' ') }
        }
        result.onSuccess { n -> refresh(); _message.value = app.resources.getQuantityString(R.plurals.restored_songs, n, n); done(null) }
            .onFailure { e ->
                done(app.getString(when (e) {
                    is WrongPasswordException -> R.string.backup_wrong_password
                    is NotABackupException -> R.string.backup_not_a_backup
                    else -> R.string.import_failed
                }))
            }
    }

    // AI connection (the player's own model) and song search

    fun saveAi(connection: AiConnection) {
        aiSettings.save(connection)
        _ai.value = aiSettings.load()
    }

    fun clearAi() {
        aiSettings.clear()
        _ai.value = aiSettings.load()
    }

    fun resetJobs() {
        _aiJob.value = Job()
        _search.value = Job()
    }

    /** Sends a small request to check the address, model and key. */
    fun testAi(connection: AiConnection) = viewModelScope.launch {
        _aiJob.value = Job(running = true)
        _aiJob.value = runCatching {
            AiClient.chat(connection, ChatRequest(connection.model, listOf(ChatMessage("user", "Answer with the word OK."))))
        }.fold({ Job(result = it.take(80)) }, { Job(error = it.message) })
    }

    /** Asks the player's AI for a tab. The result is saved as a new song; the original is not changed. */
    fun askAi(task: AiTask, level: Level, request: String, song: Song?, done: (Song) -> Unit) = viewModelScope.launch {
        val connection = _ai.value
        _aiJob.value = Job(running = true)
        val result = runCatching { AiClient.makeSong(connection, task, level, request, song) }
        result.onSuccess { made ->
            val saved = withContext(Dispatchers.IO) { repo.save(made) }
            refresh()
            _aiJob.value = Job()
            done(saved)
        }.onFailure { _aiJob.value = Job(error = it.message) }
    }

    fun searchSongs(query: String) = viewModelScope.launch {
        if (query.isBlank()) return@launch
        _search.value = Job(running = true)
        _search.value = runCatching { MusicBrainzClient.search(query) }.fold({ Job(result = it) }, { Job(error = it.message) })
    }

    // Metronome screen

    fun updateMetronome(change: (MetronomeSettings) -> MetronomeSettings) {
        _metronome.update { change(it).let { s -> s.copy(bpm = s.bpm.coerceIn(MIN_BPM, MAX_BPM)) } }
        val s = _metronome.value
        player.beatsPerBar = s.beatsPerBar
        player.subdivisions = s.subdivisions
    }

    fun tapTempo() {
        tap.tap(SystemClock.elapsedRealtime())?.let { bpm -> updateMetronome { it.copy(bpm = bpm) } }
    }

    fun startMetronome() {
        stop()
        val s = _metronome.value
        val trainer = SpeedTrainer(s.bpm, s.trainerTarget, s.trainerStep, s.trainerBars)
        player.beatsPerBar = s.beatsPerBar
        player.subdivisions = s.subdivisions
        player.tempoForBar = { bar ->
            val m = _metronome.value
            (if (m.trainer) trainer.tempoForBar(bar) else m.bpm).toDouble()
        }
        begin(null)
    }

    // Play along with a song

    /** Bars to play, in order, for the options. */
    fun bars(song: Song, options: PlayOptions): List<PlayedBar> =
        song.playOrder().filter { options.loopSection == null || it.section == options.loopSection }

    fun startSong(song: Song, options: PlayOptions) {
        stop()
        val bars = bars(song, options)
        if (bars.isEmpty()) return
        val base = (song.tempo * options.tempoPercent / 100).coerceIn(MIN_BPM, MAX_BPM)
        val trainer = SpeedTrainer((song.tempo * 0.6).toInt().coerceAtLeast(MIN_BPM), song.tempo, step = 5, barsPerStep = bars.size.coerceAtMost(8))
        val countIn = if (options.countIn) 1 else 0
        player.beatsPerBar = song.beatsPerBar
        player.subdivisions = 1
        player.tempoForBar = { bar ->
            val songBar = bar - countIn
            when {
                // A loop repeats until stopped; the whole song plays once.
                options.loopSection == null && songBar >= bars.size -> null
                options.trainer -> trainer.tempoForBar(maxOf(0, songBar)).toDouble()
                else -> base.toDouble()
            }
        }
        begin(song.id, countIn)
    }

    private var countInBars = 0

    private fun begin(songId: String?, countIn: Int = 0) {
        countInBars = countIn
        sessionStart = SystemClock.elapsedRealtime()
        bestTempo = 0
        player.start()
        _playing.value = Playing(songId, -countIn, 0, 0, player.bpm()?.toInt() ?: 0)
        viewModelScope.launch {
            while (player.playing) {
                // Sixteenth-note steps; screens convert to the pattern's own steps.
                player.position(STEPS_PER_BEAT)?.let { p ->
                    val bpm = player.bpm()?.toInt() ?: 0
                    bestTempo = maxOf(bestTempo, bpm)
                    _playing.value = Playing(songId, p.bar - countInBars, p.beat, p.step, bpm)
                }
                kotlinx.coroutines.delay(16)
            }
        }
    }

    fun stop() {
        val was = _playing.value ?: return
        player.stop()
        _playing.value = null
        val seconds = ((SystemClock.elapsedRealtime() - sessionStart) / 1000).toInt()
        if (was.songId != null) {
            val entry = PracticeEntry(was.songId, LocalDate.now().toEpochDay(), seconds, bestTempo)
            viewModelScope.launch {
                withContext(Dispatchers.IO) { repo.logPractice(entry) }
                _log.value = withContext(Dispatchers.IO) { repo.practiceLog() }
            }
        }
    }

    override fun onCleared() {
        player.stop()
    }
}
