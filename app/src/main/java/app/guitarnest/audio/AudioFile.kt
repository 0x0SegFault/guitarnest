package app.guitarnest.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import app.guitarnest.listen.BasicPitch
import app.guitarnest.listen.Resample
import java.nio.ByteOrder

class AudioException(message: String) : Exception(message)

/**
 * Decodes an audio file the player picked (mp3, m4a/aac, flac, ogg/opus, wav...) with Android's own
 * decoders, to mono 22050 Hz. The file is read through the file picker's permission only; nothing is copied.
 */
object AudioFile {
    const val MAX_SECONDS = 15 * 60

    fun decode(context: Context, uri: Uri, progress: (Float) -> Unit = {}): FloatArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw AudioException("No audio in this file")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            if (durationUs > MAX_SECONDS * 1_000_000L) throw AudioException(context.getString(app.guitarnest.R.string.listen_too_long))
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            val chunks = ArrayList<FloatArray>()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            try {
                while (!outputDone) {
                    if (!inputDone) {
                        val i = codec.dequeueInputBuffer(10_000)
                        if (i >= 0) {
                            val size = extractor.readSampleData(codec.getInputBuffer(i)!!, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(i, 0, size, extractor.sampleTime, 0)
                                if (durationUs > 0) progress((extractor.sampleTime.toFloat() / durationUs).coerceIn(0f, 1f))
                                extractor.advance()
                            }
                        }
                    }
                    val o = codec.dequeueOutputBuffer(info, 10_000)
                    when {
                        o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val f = codec.outputFormat
                            rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = f.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                        o >= 0 -> {
                            val buffer = codec.getOutputBuffer(o)!!.order(ByteOrder.LITTLE_ENDIAN)
                            buffer.position(info.offset).limit(info.offset + info.size)
                            chunks += if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                                FloatArray(info.size / 4).also { buffer.asFloatBuffer().get(it) }
                            } else {
                                val shorts = ShortArray(info.size / 2).also { buffer.asShortBuffer().get(it) }
                                FloatArray(shorts.size) { shorts[it] / 32768f }
                            }
                            codec.releaseOutputBuffer(o, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        }
                    }
                }
            } finally {
                codec.stop()
                codec.release()
            }
            val all = FloatArray(chunks.sumOf { it.size })
            var pos = 0
            for (c in chunks) { c.copyInto(all, pos); pos += c.size }
            if (all.isEmpty()) throw AudioException("No audio in this file")
            return Resample.toRate(Resample.mono(all, channels), rate, BasicPitch.SAMPLE_RATE)
        } catch (e: AudioException) {
            throw e
        } catch (e: Exception) {
            throw AudioException("Cannot read this audio file (${e.javaClass.simpleName})")
        } finally {
            extractor.release()
        }
    }
}
