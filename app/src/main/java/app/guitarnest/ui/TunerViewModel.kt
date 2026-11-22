package app.guitarnest.ui

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.guitarnest.BuildConfig
import app.guitarnest.audio.DemoSignal
import app.guitarnest.audio.Microphone
import app.guitarnest.tuner.PitchDetector
import app.guitarnest.tuner.TunerTracker
import app.guitarnest.tuner.Tuning
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TunerState(
    val tuning: Tuning = Tuning.STANDARD,
    val a4: Int = 440,
    /** Null: pick the nearest string automatically. */
    val lockedString: Int? = null,
    val frequency: Double? = null,
    /** False while an older reading is kept on screen (shown dimmed). */
    val fresh: Boolean = true,
    val listening: Boolean = false,
    val micError: Boolean = false,
) {
    val activeString: Int? get() = lockedString ?: frequency?.let { tuning.nearestString(it, a4.toDouble()) }
}

class TunerViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("tuner", Context.MODE_PRIVATE)
    private val mic = Microphone(app)
    private val detector = PitchDetector(mic.sampleRate)
    private val demoHz: Float? = if (BuildConfig.DEBUG) handle.get<Float>("demo_hz") else null
    private val tracker = TunerTracker()
    private var job: Job? = null

    private val _state = MutableStateFlow(
        TunerState(
            tuning = Tuning.byId(prefs.getString(KEY_TUNING, null) ?: "") ?: Tuning.STANDARD,
            a4 = prefs.getInt(KEY_A4, 440),
        )
    )
    val state: StateFlow<TunerState> = _state.asStateFlow()

    fun start() {
        if (job?.isActive == true) return
        _state.update { it.copy(listening = true, micError = false) }
        job = viewModelScope.launch {
            val hop = detector.windowSize / 4
            val source = demoHz?.let { DemoSignal(mic.sampleRate, it.toDouble()).windows(detector.windowSize, hop) }
                ?: mic.windows(detector.windowSize, hop)
            source
                .catch { _state.update { it.copy(listening = false, micError = true) } }
                .collect { onWindow(it) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        tracker.reset()
        _state.update { it.copy(listening = false, frequency = null) }
    }

    fun selectTuning(tuning: Tuning) {
        prefs.edit { putString(KEY_TUNING, tuning.id) }
        _state.update { it.copy(tuning = tuning, lockedString = null) }
    }

    fun setA4(value: Int) {
        val a4 = value.coerceIn(415, 466)
        prefs.edit { putInt(KEY_A4, a4) }
        _state.update { it.copy(a4 = a4) }
    }

    /** Tap a string to lock it; tap it again to return to automatic. */
    fun toggleString(index: Int) =
        _state.update { it.copy(lockedString = if (it.lockedString == index) null else index) }

    private fun onWindow(samples: FloatArray) {
        val reading = tracker.update(detector.detect(samples), SystemClock.elapsedRealtime())
        _state.update { it.copy(frequency = reading.frequency, fresh = reading.fresh) }
    }

    override fun onCleared() = stop()

    private companion object {
        const val KEY_TUNING = "tuning"
        const val KEY_A4 = "a4"
    }
}
