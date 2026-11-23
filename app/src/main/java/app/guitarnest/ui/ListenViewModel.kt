package app.guitarnest.ui

import android.app.Application
import android.media.AudioManager
import android.media.projection.MediaProjection
import android.net.Uri
import android.os.Build
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.guitarnest.ai.AiTask
import app.guitarnest.audio.AudioFile
import app.guitarnest.audio.Recorder
import app.guitarnest.data.AiSettings
import app.guitarnest.data.SongRepository
import app.guitarnest.listen.AcoustId
import app.guitarnest.listen.Arranger
import app.guitarnest.listen.BasicPitch
import app.guitarnest.net.AcoustIdClient
import app.guitarnest.net.AiClient
import app.guitarnest.net.makeSong
import app.guitarnest.tab.Level
import app.guitarnest.tab.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

enum class Step { READING, LISTENING, IDENTIFYING, ARRANGING, ASKING_AI }

sealed interface ListenState {
    data object Idle : ListenState
    data class Recording(val fromApps: Boolean, val seconds: Float, val level: Float) : ListenState
    data class Working(val step: Step, val progress: Float) : ListenState
    data class Failed(val message: String) : ListenState
}

/**
 * Audio to tab on the phone: a file the player owns, a microphone recording, or the sound of an app
 * that allows capture. Notes are found by Basic Pitch on this phone; the player's own AI may then refine the draft.
 */
class ListenViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SongRepository(app)
    private val ai = AiSettings(app)
    private val model by lazy { BasicPitch(app.assets.open("basic-pitch.gnm").use { it.readBytes() }) }
    private var recorder: Recorder? = null

    private val _state = MutableStateFlow<ListenState>(ListenState.Idle)
    val state: StateFlow<ListenState> = _state.asStateFlow()

    /** Set when a song was made; the screen opens it. */
    private val _made = MutableStateFlow<Song?>(null)
    val made: StateFlow<Song?> = _made.asStateFlow()

    private val prefs = app.getSharedPreferences("listen", android.content.Context.MODE_PRIVATE)

    /** "Find the title": off by default, because it sends a fingerprint to AcoustID. */
    var identify: Boolean
        get() = AcoustId.available && prefs.getBoolean("identify", false)
        set(value) { prefs.edit { putBoolean("identify", value) } }

    /** True when the AI connection is set, so the screen can offer "Improve with my AI". */
    fun aiConfigured() = ai.load().configured

    fun reset() {
        _state.value = ListenState.Idle
        _made.value = null
    }

    fun fromFile(uri: Uri, title: String, fileName: String, level: Level, refine: Boolean) = viewModelScope.launch {
        _state.value = ListenState.Working(Step.READING, 0f)
        val audio = runCatching {
            withContext(Dispatchers.IO) { AudioFile.decode(getApplication(), uri) { p -> _state.value = ListenState.Working(Step.READING, p) } }
        }.getOrElse { _state.value = ListenState.Failed(it.message ?: "Error"); return@launch }
        make(audio, title, level, refine, fallbackTitle = fileName)
    }

    fun startMicrophone() = startRecorder(fromApps = false) {
        val unprocessed = getApplication<Application>().getSystemService(AudioManager::class.java)
            .getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        Recorder.microphone(unprocessed)
    }

    fun startCapture(projection: MediaProjection) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startRecorder(fromApps = true) { Recorder.playback(projection) }
    }

    private fun startRecorder(fromApps: Boolean, create: () -> Recorder) {
        val r = runCatching(create).getOrElse { _state.value = ListenState.Failed(it.message ?: "Error"); return }
        recorder = r
        r.start()
        viewModelScope.launch {
            while (r.active) {
                _state.value = ListenState.Recording(fromApps, r.seconds, r.level)
                delay(100)
            }
        }
    }

    fun stopAndMake(title: String, level: Level, refine: Boolean) = viewModelScope.launch {
        val r = recorder ?: return@launch
        recorder = null
        val audio = withContext(Dispatchers.Default) { r.stop() }
        if (audio.size < BasicPitch.SAMPLE_RATE * 3) {
            _state.value = ListenState.Failed(getApplication<Application>().getString(app.guitarnest.R.string.listen_too_short))
            return@launch
        }
        make(audio, title, level, refine)
    }

    fun cancelRecording() {
        recorder?.stop()
        recorder = null
        _state.value = ListenState.Idle
    }

    private suspend fun make(audio: FloatArray, title: String, level: Level, refine: Boolean, fallbackTitle: String = "") {
        try {
            val notes = withContext(Dispatchers.Default) {
                val padded = model.padded(audio)
                val total = model.windowCount(audio.size)
                val done = AtomicInteger()
                // Windows are independent: use all cores, but keep memory bounded.
                val permits = Semaphore(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
                val windows = (0 until total).map { k ->
                    async {
                        permits.withPermit {
                            model.window(padded, k).also {
                                _state.value = ListenState.Working(Step.LISTENING, done.incrementAndGet().toFloat() / total)
                            }
                        }
                    }
                }.awaitAll()
                model.notesFrom(audio.size, windows)
            }
            if (notes.size < 8) {
                _state.value = ListenState.Failed(getApplication<Application>().getString(app.guitarnest.R.string.listen_no_notes))
                return
            }
            var problem: String? = null
            var name = title.trim()
            var artist = ""
            if (name.isEmpty() && identify) {
                _state.value = ListenState.Working(Step.IDENTIFYING, 0f)
                val fp = withContext(Dispatchers.Default) { AcoustId.fingerprint(audio, BasicPitch.SAMPLE_RATE) }
                val ctx = getApplication<Application>()
                if (fp == null) problem = ctx.getString(app.guitarnest.R.string.identify_too_short)
                else runCatching { AcoustIdClient.identify(fp.first, fp.second) }
                    .onSuccess { found -> found.firstOrNull()?.let { name = it.title; artist = it.artist } ?: run { problem = ctx.getString(app.guitarnest.R.string.identify_none) } }
                    .onFailure { problem = it.message }
            }
            _state.value = ListenState.Working(Step.ARRANGING, 0f)
            var song = withContext(Dispatchers.Default) {
                Arranger.makeSong(notes, level, name.ifBlank { fallbackTitle.ifBlank { "Recording" } }).copy(artist = artist)
            }
            val connection = ai.load()
            if (refine && connection.configured) {
                _state.value = ListenState.Working(Step.ASKING_AI, 0f)
                runCatching { AiClient.makeSong(connection, AiTask.REFINE, level, "", song) }
                    .onSuccess { refined -> song = refined.copy(title = song.title, artist = song.artist.ifBlank { refined.artist }, notes = refined.notes.ifBlank { song.notes }) }
                    .onFailure { problem = getApplication<Application>().getString(app.guitarnest.R.string.listen_ai_failed, it.message) }
            }
            val saved = withContext(Dispatchers.IO) { repo.save(song) }
            _state.value = problem?.let { ListenState.Failed(it) } ?: ListenState.Idle
            _made.value = saved
        } catch (e: Exception) {
            _state.value = ListenState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    override fun onCleared() {
        recorder?.stop()
    }
}
