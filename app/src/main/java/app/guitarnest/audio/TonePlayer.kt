package app.guitarnest.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** Plays a short sound made in memory (reference notes for the tuner). A new sound stops the previous one. */
class TonePlayer(val sampleRate: Int = 48_000) {
    private var track: AudioTrack? = null

    fun play(samples: FloatArray) {
        stop()
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(samples.size * 4)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        t.play()
        track = t
    }

    fun stop() {
        track?.run { runCatching { stop() }; release() }
        track = null
    }
}
