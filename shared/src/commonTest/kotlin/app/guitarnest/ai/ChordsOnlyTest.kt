package app.guitarnest.ai

import app.guitarnest.tab.Bar
import app.guitarnest.tab.Beat
import app.guitarnest.tab.ChordMark
import app.guitarnest.tab.Level
import app.guitarnest.tab.Section
import app.guitarnest.tab.Song
import app.guitarnest.tab.TabNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChordsOnlyTest {
    @Test
    fun theRequestAsksForChordsOnlyAndNamesTheSong() {
        val r = AiTabMaker.request("m", AiTask.CHORDS, Level.BEGINNER, "Night Walk – Ana", null)
        val user = r.messages.last().content
        assertTrue("Night Walk – Ana" in user)
        assertTrue("no lyrics" in user && "no melody" in user)
        assertTrue(AiTabMaker.UNKNOWN_SONG_ANSWER in user)
    }

    @Test
    fun anUnknownSongAnswerIsRecognised() {
        assertTrue(AiTabMaker.unknownSong("""{"error": "unknown song"}"""))
        assertTrue(AiTabMaker.unknownSong("```json\n{\"error\":\"Unknown song\"}\n```"))
        assertFalse(AiTabMaker.unknownSong("""{"title": "A", "sections": []}"""))
    }

    @Test
    fun singleNotesAndFreeTextAreRemoved() {
        val song = Song(
            title = "A", notes = "some words",
            sections = listOf(Section("Verse", listOf(Bar(chords = listOf(ChordMark(0.0, "G")), beats = listOf(Beat(0.0, listOf(TabNote(1, 3)))))))),
        )
        val only = AiTabMaker.chordsOnly(song)
        assertEquals("", only.notes)
        assertEquals(listOf(ChordMark(0.0, "G")), only.sections[0].bars[0].chords)
        assertTrue(only.sections[0].bars[0].beats.isEmpty())
    }

    @Test
    fun theIphoneTaskIndexesStayTheSame() {
        assertEquals(listOf("ARRANGE", "STRUMMING", "WRITE", "REFINE", "CHORDS"), AiTask.entries.map { it.name })
    }
}
