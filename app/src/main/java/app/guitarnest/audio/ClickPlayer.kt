package app.guitarnest.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import app.guitarnest.practice.ClickKind
import app.guitarnest.practice.ClickTrack
import app.guitarnest.practice.Position
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Metronome audio. Clicks are mixed into the sample stream at exact positions, so the timing
 * does not depend on the UI or on thread scheduling. The tempo can change at each bar line.
 */
class ClickPlayer(private val sampleRate: Int = 48_000) {
    /** Called at each bar line from the audio thread: the tempo for that bar, or null to stop. */
    @Volatile var tempoForBar: (bar: Int) -> Double? = { null }
    @Volatile var beatsPerBar: Int = 4
    @Volatile var subdivisions: Int = 1
    @Volatile var accent: Boolean = true
    /** Called from the audio thread when [tempoForBar] returns null (end of the song). */
    @Volatile var onEnd: () -> Unit = {}

    private data class Segment(val startSample: Long, val startBar: Int, val track: ClickTrack)

    @Volatile private var segments = listOf<Segment>()
    @Volatile private var thread: Thread? = null
    private var audio: AudioTrack? = null

    private val sounds = mapOf(
        ClickKind.BAR to tone(1760.0, 0.9),
        ClickKind.BEAT to tone(1175.0, 0.6),
        ClickKind.SUBDIVISION to tone(880.0, 0.3),
    )

    val playing: Boolean get() = thread != null

    fun start() {
        if (thread != null) return
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(maxOf(minBuffer, CHUNK * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        audio = track
        val first = tempoForBar(0) ?: return
        segments = listOf(Segment(0, 0, ClickTrack(first, beatsPerBar, subdivisions, sampleRate)))
        track.play()
        thread = Thread({ writeLoop(track) }, "metronome").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        val t = thread ?: return
        thread = null
        t.interrupt()
        t.join(500)
        audio?.run { pause(); flush(); release() }
        audio = null
    }

    /** Position of the sound that is playing now, or null when stopped. */
    fun position(stepsPerBeat: Int = 2): Position? {
        val track = audio ?: return null
        if (thread == null) return null
        val heard = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
        val segment = segments.lastOrNull { it.startSample <= heard } ?: return null
        val p = segment.track.positionAt(heard - segment.startSample, stepsPerBeat)
        return p.copy(bar = p.bar + segment.startBar)
    }

    /** Current tempo (of the segment that is playing). */
    fun bpm(): Double? = segments.lastOrNull()?.track?.bpm

    private fun writeLoop(track: AudioTrack) {
        val buffer = ShortArray(CHUNK)
        val mix = FloatArray(CHUNK)
        var cursor = 0L
        val ringing = mutableListOf<IntArray>() // [sound index, offset into sound]
        val soundList = ClickKind.entries.map { sounds.getValue(it) }
        try {
            while (!Thread.currentThread().isInterrupted) {
                mix.fill(0f)
                // Continue clicks that started in the previous chunk.
                val iterator = ringing.iterator()
                while (iterator.hasNext()) {
                    val r = iterator.next()
                    val sound = soundList[r[0]]
                    var i = 0
                    while (i < CHUNK && r[1] < sound.size) { mix[i++] += sound[r[1]++] }
                    if (r[1] >= sound.size) iterator.remove()
                }
                var from = cursor
                val until = cursor + CHUNK
                while (from < until) {
                    var segment = segments.last()
                    val local = segment.track.clicksIn(from - segment.startSample, until - segment.startSample)
                    var next = until
                    for (click in local) {
                        val at = segment.startSample + click.sample
                        if (click.kind == ClickKind.BAR) {
                            val bar = segment.startBar + (click.sample / (segment.track.samplesPerBeat * segment.track.beatsPerBar)).toInt()
                            val bpm = tempoForBar(bar)
                            if (bpm == null) { Thread.currentThread().interrupt(); next = at; break }
                            if (bpm != segment.track.bpm || beatsPerBar != segment.track.beatsPerBar || subdivisions != segment.track.subdivisions) {
                                if (at > segment.startSample) {
                                    segment = Segment(at, bar, ClickTrack(bpm, beatsPerBar, subdivisions, sampleRate))
                                    segments = (segments + segment).takeLast(4)
                                    next = at
                                    break
                                }
                            }
                        }
                        val kind = if (!accent && click.kind == ClickKind.BAR) ClickKind.BEAT else click.kind
                        val offset = (at - cursor).toInt()
                        val sound = soundList[kind.ordinal]
                        var i = 0
                        while (offset + i < CHUNK && i < sound.size) { mix[offset + i] += sound[i]; i++ }
                        if (i < sound.size) ringing += intArrayOf(kind.ordinal, i)
                    }
                    if (next == until || local.isEmpty()) break
                    from = next
                    if (Thread.currentThread().isInterrupted) break
                }
                for (i in 0 until CHUNK) buffer[i] = (mix[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
                track.write(buffer, 0, CHUNK)
                cursor = until
            }
        } catch (_: IllegalStateException) {
            // Track released while writing: stopping.
        }
        // Ended by the music, not by stop(): let the last click ring, then report.
        if (thread != null) {
            Thread.sleep(300)
            onEnd()
        }
    }

    private fun tone(frequency: Double, gain: Double): FloatArray {
        val n = (sampleRate * 0.03).toInt()
        return FloatArray(n) { i -> (gain * exp(-i / (sampleRate * 0.006)) * sin(2 * PI * frequency * i / sampleRate)).toFloat() }
    }

    private companion object {
        const val CHUNK = 512
    }
}
