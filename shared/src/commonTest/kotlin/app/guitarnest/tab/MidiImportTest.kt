package app.guitarnest.tab

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MidiImportTest {
    /** A small MIDI file written for this test: 3/4 at 120 BPM, a G chord then a melody. */
    private fun file(): ByteArray {
        val track = mutableListOf<Int>()
        fun v(n: Int) { track += n } // delta times below 128 fit one byte
        // name, tempo 500000 us, 3/4
        track += listOf(0, 0xFF, 0x03, 4) + "Test".map { it.code }
        track += listOf(0, 0xFF, 0x51, 3, 0x07, 0xA1, 0x20)
        track += listOf(0, 0xFF, 0x58, 4, 3, 2, 24, 8)
        // G chord (G2 B2 D3 G3) on beat 1 for 2 beats (division 96: 2 beats = 192 = 0x81 0x40)
        track += listOf(0, 0x90, 43, 90, 0, 47, 90, 0, 50, 90, 0, 55, 90)
        track += listOf(0x81, 0x40, 0x80, 43, 0, 0, 0x80, 47, 0, 0, 0x80, 50, 0, 0, 0x80, 55, 0)
        // melody: E4 then D4 (one beat each), running status for the note-off
        track += listOf(0, 0x90, 64, 80, 96, 0x80, 64, 0, 0, 0x90, 62, 80, 96, 62, 0)
        v(0); track += listOf(0xFF, 0x2F, 0)
        val header = listOf(0x4D, 0x54, 0x68, 0x64, 0, 0, 0, 6, 0, 0, 0, 1, 0, 96)
        val chunk = listOf(0x4D, 0x54, 0x72, 0x6B, 0, 0, 0, track.size)
        return (header + chunk + track).map { it.toByte() }.toByteArray()
    }

    @Test
    fun importsTitleTempoMeterChordsAndNotes() {
        val song = MidiImport.import(file(), "fallback")
        assertEquals("Test", song.title)
        assertEquals(120, song.tempo)
        assertEquals(3, song.beatsPerBar)
        val bar = song.sections[0].bars[0]
        assertEquals("G", bar.chords.first().name)
        assertEquals(4, bar.beats.first { it.at == 0.0 }.notes.size)
        assertTrue(song.sections.flatMap { it.bars }.sumOf { b -> b.beats.sumOf { it.notes.size } } == 6)
        assertTrue(SongCodec.validate(song).isEmpty(), SongCodec.validate(song).toString())
    }

    @Test
    fun refusesOtherFiles() {
        assertFailsWith<TabFormatException> { MidiImport.import("hello".encodeToByteArray(), "x") }
        assertFailsWith<TabFormatException> { MidiImport.import(file().copyOf(30), "x") }
    }
}
