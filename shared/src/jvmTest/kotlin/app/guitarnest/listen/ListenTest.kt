package app.guitarnest.listen

import app.guitarnest.tab.Level
import app.guitarnest.tab.SongCodec
import app.guitarnest.tuner.Tuning
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Audio to tab on the phone code path, with synthetic strummed guitar (written for this test). */
class ListenTest {
    private val pitch = BasicPitch(File("../models/basic-pitch.gnm").readBytes())
    private val shapes = mapOf(
        "G" to listOf(3, 2, 0, 0, 0, 3), "C" to listOf(null, 3, 2, 0, 1, 0),
        "D" to listOf(null, null, 0, 2, 3, 2), "Em" to listOf(0, 2, 2, 0, 0, 0),
    )

    /** Karplus-Strong strums at 22050 Hz: "D-DU-UDU" at [bpm], capo [capo]. */
    private fun strummed(chords: List<String>, bpm: Double, capo: Int, seed: Int = 3): FloatArray {
        val sr = BasicPitch.SAMPLE_RATE
        val eighth = 60 / bpm / 2
        val pattern = "D-DU-UDU"
        val audio = FloatArray(((chords.size * 8 + 4) * eighth * sr).toInt())
        val random = Random(seed)
        val hits = chords.indices.flatMap { bar -> pattern.indices.filter { pattern[it] != '-' }.map { bar * 8 + it to pattern[it] } }
        hits.forEachIndexed { k, (pos, dir) ->
            val shape = shapes.getValue(chords[pos / 8])
            var strings = (0 until 6).filter { shape[it] != null }
            if (dir == 'U') strings = strings.takeLast(4).reversed()
            val next = if (k + 1 < hits.size) hits[k + 1].first * eighth else pos * eighth + 4 * eighth
            strings.forEachIndexed { j, s ->
                val t = pos * eighth + j * 0.009
                val f = 440.0 * Math.pow(2.0, (Tuning.STANDARD.strings[s] + capo + shape[s]!! - 69) / 12.0)
                val tone = pluck(f, ((next - t) * sr).toInt(), random)
                val start = (t * sr).toInt()
                val gain = random.nextDouble(0.25, 0.4).toFloat()
                for (i in tone.indices) if (start + i < audio.size) audio[start + i] += tone[i] * gain
            }
        }
        val peak = audio.maxOf { kotlin.math.abs(it) }
        return FloatArray(audio.size) { audio[it] / peak * 0.8f }
    }

    private fun pluck(freq: Double, n: Int, random: Random): FloatArray {
        val period = BasicPitch.SAMPLE_RATE / freq
        val length = (period - 0.6).toInt()
        val frac = period - 0.5 - length
        val c = (1 - frac) / (1 + frac)
        val line = DoubleArray(length) { random.nextDouble(-0.5, 0.5) }
        val out = FloatArray(n)
        var idx = 0; var last = 0.0; var apIn = 0.0; var apOut = 0.0
        for (i in 0 until n) {
            val v = line[idx]; out[i] = v.toFloat()
            val lp = 0.996 * 0.5 * (v + last); last = v
            val ap = c * (lp - apOut) + apIn; apIn = lp; apOut = ap
            line[idx] = ap; idx = (idx + 1) % length
        }
        val fade = minOf(n, 441)
        for (i in 0 until fade) out[n - fade + i] *= 1f - i.toFloat() / fade
        return out
    }

    @Test
    fun strummedSongWithCapo() {
        val chords = listOf("G", "C", "D", "Em", "G", "C", "D", "Em")
        val started = System.nanoTime()
        val notes = pitch.notes(strummed(chords, 90.0, 2))
        val song = Arranger.makeSong(notes, Level.INTERMEDIATE, "Test")
        println("audio to tab: ${(System.nanoTime() - started) / 1_000_000} ms, ${notes.size} notes, ${song.tempo} BPM, capo ${song.capo}, ${song.patterns[0].steps}")
        assertTrue(kotlin.math.abs(song.tempo - 90) <= 2, "tempo ${song.tempo}")
        assertEquals(2, song.capo)
        assertEquals("D-DU-UDU", song.patterns[0].steps)
        val found = song.playOrder().map { it.content.chords.firstOrNull()?.name }
        assertTrue(found.zip(chords).count { (a, b) -> a == b } >= 6, "chords $found")
        assertTrue(SongCodec.validate(song).isEmpty(), SongCodec.validate(song).toString())
        val beginner = Arranger.makeSong(notes, Level.BEGINNER, "Test")
        assertTrue(beginner.playOrder().all { it.content.chords.size <= 1 && it.content.beats.isEmpty() })
        val advanced = Arranger.makeSong(notes, Level.ADVANCED, "Test")
        assertTrue(advanced.playOrder().sumOf { it.content.beats.size } > 20)
        assertTrue(SongCodec.validate(advanced).isEmpty(), SongCodec.validate(advanced).toString())
    }

    @Test
    fun transposeAndCapo() {
        assertEquals("Em", Arranger.transpose("F#m", -2))
        assertEquals("C7", Arranger.transpose("Bb7", 2))
        assertEquals(0, Arranger.capoForOpenShapes(listOf("C", "G", "Am", "Em")))
        assertEquals(2, Arranger.capoForOpenShapes(listOf("A", "D", "E", "F#m")))
    }
}
