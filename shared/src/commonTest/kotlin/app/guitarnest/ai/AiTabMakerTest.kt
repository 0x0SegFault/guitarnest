package app.guitarnest.ai

import app.guitarnest.tab.Level
import app.guitarnest.tab.Song
import app.guitarnest.tab.SongCodec
import app.guitarnest.tab.TabFormatException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiTabMakerTest {
    private val song = Song(id = "abc", title = "Mine", tempo = 80)

    @Test
    fun requestHasFormatRulesAndSongButNoId() {
        val r = AiTabMaker.request("llama3", AiTask.ARRANGE, Level.BEGINNER, "  easier please ", song)
        assertEquals("system", r.messages[0].role)
        assertTrue("Do not reproduce published tabs" in r.messages[0].content)
        val user = r.messages[1].content
        assertTrue("beginner level" in user)
        assertTrue("Player's request: easier please" in user)
        assertTrue("\"title\": \"Mine\"" in user)
        assertFalse("abc" in user)
        assertTrue("\"model\":\"llama3\"" in AiTabMaker.encode(r))
    }

    @Test
    fun answerInCodeFenceIsRead() {
        val answer = "```json\n" + SongCodec.encode(song.copy(title = "Easy")) + "\n```"
        val result = AiTabMaker.songFrom(answer, "AI (llama3)", Level.BEGINNER)
        assertEquals("Easy", result.title)
        assertEquals("", result.id)
        assertEquals("AI (llama3)", result.source)
        assertEquals(Level.BEGINNER, result.level)
    }

    @Test
    fun badAnswersAreRefused() {
        assertFailsWith<TabFormatException> { AiTabMaker.songFrom("Sorry, I cannot.", "AI", null) }
        assertFailsWith<TabFormatException> { AiTabMaker.songFrom("""{"title": "x", "capo": 40}""", "AI", null) }
    }

    @Test
    fun chatResponse() {
        val body = """{"id":"1","choices":[{"index":0,"message":{"role":"assistant","content":"{\"title\":\"T\"}"}}],"usage":{}}"""
        assertEquals("{\"title\":\"T\"}", AiTabMaker.answerText(body))
        assertFailsWith<TabFormatException> { AiTabMaker.answerText("<html>502</html>") }
        assertFailsWith<TabFormatException> { AiTabMaker.answerText("""{"choices":[]}""") }
    }

    @Test
    fun repairAddsTheProblem() {
        val r = AiTabMaker.request("m", AiTask.WRITE, Level.ADVANCED, "blues in A", null)
        val fixed = AiTabMaker.repair(r, "{bad}", "Capo must be 0 to 12")
        assertEquals(4, fixed.messages.size)
        assertTrue("Capo must be 0 to 12" in fixed.messages.last().content)
    }
}
