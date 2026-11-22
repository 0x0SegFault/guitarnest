package app.guitarnest.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import androidx.annotation.RequiresApi
import app.guitarnest.listen.BasicPitch
import app.guitarnest.listen.Resample
import kotlin.math.sqrt

/**
 * Records into memory: the microphone, or (Android 10+) the sound of other apps that allow capture.
 * Apps that do not allow it (most streaming apps) give silence; Android enforces this.
 * Nothing is written to a file: the samples are analysed, then dropped.
 */
class Recorder private constructor(private val record: AudioRecord, private val rate: Int) {
    @Volatile var level: Float = 0f
        private set
    @Volatile var seconds: Float = 0f
        private set
    private val chunks = ArrayList<FloatArray>()
    private var thread: Thread? = null
    @Volatile private var running = false

    fun start() {
        running = true
        record.startRecording()
        thread = Thread({
            val buffer = FloatArray(rate / 10)
            var total = 0L
            while (running && total < MAX_SECONDS.toLong() * rate) {
                val n = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (n <= 0) break
                val copy = buffer.copyOf(n)
                synchronized(chunks) { chunks += copy }
                total += n
                var sum = 0f
                for (v in copy) sum += v * v
                level = sqrt(sum / n)
                seconds = total.toFloat() / rate
            }
            running = false
        }, "recorder").apply { start() }
    }

    val active: Boolean get() = running

    /** Stops and returns mono 22050 Hz audio. */
    fun stop(): FloatArray {
        running = false
        thread?.join(1000)
        runCatching { record.stop() }
        record.release()
        val all = synchronized(chunks) {
            FloatArray(chunks.sumOf { it.size }).also { out -> var p = 0; for (c in chunks) { c.copyInto(out, p); p += c.size } }
        }
        return Resample.toRate(all, rate, BasicPitch.SAMPLE_RATE)
    }

    companion object {
        const val MAX_SECONDS = 10 * 60
        private const val RATE = 44_100
        private const val ENCODING = AudioFormat.ENCODING_PCM_FLOAT

        private fun bufferSize() = maxOf(AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, ENCODING), RATE)

        /** Microphone. Caller holds RECORD_AUDIO. UNPROCESSED when available: no gain control or noise suppression. */
        @SuppressLint("MissingPermission")
        fun microphone(unprocessed: Boolean): Recorder {
            val source = if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.MIC
            val r = AudioRecord(source, RATE, AudioFormat.CHANNEL_IN_MONO, ENCODING, bufferSize())
            check(r.state == AudioRecord.STATE_INITIALIZED) { "Microphone not available" }
            return Recorder(r, RATE)
        }

        /** Sound of other apps that allow capture (media and games). Caller holds RECORD_AUDIO and a running projection. */
        @SuppressLint("MissingPermission")
        @RequiresApi(Build.VERSION_CODES.Q)
        fun playback(projection: MediaProjection): Recorder {
            val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(android.media.AudioAttributes.USAGE_GAME)
                .addMatchingUsage(android.media.AudioAttributes.USAGE_UNKNOWN)
                .build()
            val r = AudioRecord.Builder()
                .setAudioFormat(AudioFormat.Builder().setEncoding(ENCODING).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                .setBufferSizeInBytes(bufferSize())
                .setAudioPlaybackCaptureConfig(config)
                .build()
            check(r.state == AudioRecord.STATE_INITIALIZED) { "Capture not available" }
            return Recorder(r, RATE)
        }
    }
}
