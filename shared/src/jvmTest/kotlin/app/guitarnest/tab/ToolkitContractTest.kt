package app.guitarnest.tab

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Files written by the AI toolkit (ai/gntab) must load in the app. Regenerate them when the format changes. */
class ToolkitContractTest {
    private fun load(name: String) = SongCodec.decode(javaClass.getResource("/$name")!!.readText())

    @Test
    fun intermediateSample() {
        val song = load("toolkit-sample.gnt")
        assertEquals(Level.INTERMEDIATE, song.level)
        assertEquals(2, song.capo)
        assertEquals("D-DU-UDU", song.patterns.single().steps)
        assertTrue(song.playOrder().size >= 8)
        assertTrue(TextTab.printLines(song).isNotEmpty())
    }

    @Test
    fun advancedSampleHasNotes() {
        val song = load("toolkit-sample-advanced.gnt")
        val beats = song.sections.flatMap { it.bars }.flatMap { it.beats }
        assertTrue(beats.size > 10)
        assertTrue(beats.flatMap { it.notes }.all { it.string in 1..6 })
    }
}
