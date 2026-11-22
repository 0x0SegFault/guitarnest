package app.guitarnest.tab

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextTabTest {
    private val bars = listOf(
        Bar(chords = listOf(ChordMark(0.0, "Am")), beats = listOf(Beat(0.0, listOf(TabNote(5, 0))), Beat(0.5, listOf(TabNote(3, 2))), Beat(1.0, listOf(TabNote(2, 1, "h"))))),
        Bar(chords = listOf(ChordMark(0.0, "C"), ChordMark(2.0, "G")), beats = listOf(Beat(0.0, listOf(TabNote(2, 1), TabNote(5, 3))), Beat(2.0, listOf(TabNote(1, 12, "h"))), Beat(3.75, listOf(TabNote(1, 12))))),
    )

    @Test
    fun sectionTextRoundTrip() {
        val text = TextTab.sectionText(Section("A", bars), 4)
        val lines = text.lines()
        assertEquals("| Am . . . | C . G . |", lines[0])
        assertEquals(7, lines.size)
        assertTrue(lines[2].startsWith("B|"))
        assertEquals(bars, TextTab.parseSection(text, 4).getOrThrow())
    }

    @Test
    fun handWrittenTabIsRead() {
        val text = """
            G . D .
            e|--------|
            B|--------|
            G|--------|
            D|----2---|
            A|--------|
            E|0-------|
        """.trimIndent()
        val parsed = TextTab.parseSection(text, 4).getOrThrow()
        assertEquals(1, parsed.size)
        assertEquals(listOf(ChordMark(0.0, "G"), ChordMark(2.0, "D")), parsed[0].chords)
        assertEquals(Beat(0.0, listOf(TabNote(6, 0))), parsed[0].beats[0])
        assertEquals(2.0, parsed[0].beats[1].at)
    }

    @Test
    fun chordOnlyAndErrors() {
        assertEquals(listOf(ChordMark(0.0, "Em")), TextTab.parseSection("| Em | C |", 4).getOrThrow()[0].chords)
        assertTrue(TextTab.parseSection("e|--|\nB|--|", 4).isFailure)
    }

    @Test
    fun printLinesHaveHeaderAndNotice() {
        val song = Song(title = "Song", artist = "Me", capo = 3, patterns = listOf(StrumPattern("a", "A", "D-DU")), sections = listOf(Section("Intro", bars, strum = "a")))
        val lines = TextTab.printLines(song)
        assertEquals("Song – Me", lines[0])
        assertTrue(lines[1].contains("Capo: 3"))
        assertTrue(lines.any { it.startsWith("Strumming A: ↓ · ↓ ↑") })
        assertTrue(lines.any { it.startsWith("[Intro]") })
        assertTrue(lines.last().startsWith("For personal practice only"))
        assertTrue(lines.all { it.length <= 80 })
    }
}
