package app.guitarnest.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Live microphone input as overlapping windows of float samples.
 * Audio stays in memory: it is never written to a file or sent anywhere.
 */
class Microphone(private val context: Context) {
    val sampleRate: Int = (context.getSystemService(AudioManager::class.java)
        .getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48_000)
        .coerceIn(16_000, 48_000)

    /** Emits [windowSize] samples every [hop] new samples. Caller must hold RECORD_AUDIO. */
    @SuppressLint("MissingPermission")
    fun windows(windowSize: Int, hop: Int): Flow<FloatArray> = flow {
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, CHANNEL, ENCODING)
        val record = AudioRecord(source(), sampleRate, CHANNEL, ENCODING, maxOf(minBuffer, hop * 4 * 4))
        check(record.state == AudioRecord.STATE_INITIALIZED) { "Microphone not available" }
        val window = FloatArray(windowSize)
        val chunk = FloatArray(hop)
        try {
            record.startRecording()
            while (true) {
                currentCoroutineContext().ensureActive()
                var read = 0
                while (read < hop) {
                    val n = record.read(chunk, read, hop - read, AudioRecord.READ_BLOCKING)
                    check(n >= 0) { "Microphone read failed: $n" }
                    read += n
                }
                System.arraycopy(window, hop, window, 0, windowSize - hop)
                System.arraycopy(chunk, 0, window, windowSize - hop, hop)
                emit(window.copyOf())
            }
        } finally {
            record.stop()
            record.release()
        }
    }.flowOn(Dispatchers.Default)

    /** UNPROCESSED skips noise suppression and gain control, which distort pitch; not all phones have it. */
    private fun source(): Int {
        val unprocessed = context.getSystemService(AudioManager::class.java)
            .getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        return if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
    }

    private companion object {
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_FLOAT
    }
}
