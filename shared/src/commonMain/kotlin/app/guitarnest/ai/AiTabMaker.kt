package app.guitarnest.ai

import app.guitarnest.tab.Level
import app.guitarnest.tab.Song
import app.guitarnest.tab.SongCodec
import app.guitarnest.tab.TabFormatException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What the player asks the AI to do. */
enum class AiTask { ARRANGE, STRUMMING, WRITE, REFINE }

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
data class ChatRequest(val model: String, val messages: List<ChatMessage>, val temperature: Double = 0.3)

@Serializable
private data class ChatResponse(val choices: List<Choice> = emptyList()) {
    @Serializable data class Choice(val message: ChatMessage? = null)
}

/** Anthropic Messages API: the system prompt is a separate field, and max_tokens is required. */
@Serializable
private data class AnthropicRequest(
    val model: String,
    @kotlinx.serialization.SerialName("max_tokens") val maxTokens: Int,
    val system: String? = null,
    val messages: List<ChatMessage>,
    val temperature: Double,
)

@Serializable
private data class AnthropicResponse(val content: List<Block> = emptyList()) {
    @Serializable data class Block(val type: String = "", val text: String? = null)
}

/** The two API styles. Every other provider (local or cloud) speaks the OpenAI style. */
enum class ApiStyle { OPENAI, ANTHROPIC }

/**
 * Builds requests for any OpenAI-compatible chat endpoint (LiteLLM, Ollama, LM Studio, vLLM,
 * OpenRouter, OpenAI, Mistral...) and turns the answer into a checked GuitarNest song.
 * Only text is sent: the song, the level and the player's request. Never audio, never other songs.
 */
object AiTabMaker {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val SYSTEM = """
You write guitar tabs in the GuitarNest format (.gnt), a JSON object:
{"title": str, "artist": str, "source": str, "level": "beginner"|"intermediate"|"advanced",
 "tuning": "standard"|"half_step_down"|"whole_step_down"|"drop_d"|"drop_c"|"dadgad"|"open_d"|"open_g"|"open_e" or six notes low to high like "D2 A2 D3 G3 B3 E4",
 "capo": 0-12, "tempo": BPM 20-300, "beatsPerBar": 1-12,
 "patterns": [{"id": str, "name": str, "steps": str of D (down) U (up) x (muted) - (no stroke), "perBeat": 2 or 4}],
 "sections": [{"name": str, "repeat": int, "strum": pattern id or null,
   "bars": [{"chords": [{"beat": number from 0, "name": "G"}],
             "beats": [{"at": beat from 0 (quarter notes, multiples of 0.25), "notes": [{"string": 1-6 (1 = high E), "fret": 0-24, "tech": optional "h"|"p"|"/"|"\\"|"b"|"~"|"x"}]}],
             "strum": optional pattern id}]}],
 "notes": str}
Frets are counted from the capo. A strumming pattern has beatsPerBar * perBeat steps, for example "D-DU-UDU" for 4/4 in eighths.
Levels:
- beginner: open chords (use a capo to avoid barre chords), one simple strumming pattern, few or no single notes.
- intermediate: full chords, the real strumming pattern, short riffs.
- advanced: the full part as played, with techniques.
Rules:
- Work from the material the player gives you: their tab, chords, a draft made from their own recording, or their description.
- Use your music knowledge to correct and complete it: key, chord choice, half or double tempo, capo, strumming feel, form. Simple facts about a known song (key, tuning, capo, tempo) are fine.
- Do not reproduce published tabs, chord sheets or lyrics from memory.
- Answer with the JSON object only. No text before or after it.
""".trim()

    fun request(model: String, task: AiTask, level: Level, request: String, song: Song?): ChatRequest {
        val ask = when (task) {
            AiTask.ARRANGE -> "Arrange this song for the ${level.name.lowercase()} level. Keep the title, artist, key and form."
            AiTask.STRUMMING -> "Add strumming patterns that fit each section of this song. Keep everything else."
            AiTask.WRITE -> "Write a new ${level.name.lowercase()} tab from the player's description."
            AiTask.REFINE -> "This draft was made automatically from a recording; tempo, chords, capo and strumming are estimates. " +
                "Check and correct it with your music knowledge, keep what is right, and arrange it for the ${level.name.lowercase()} level."
        }
        val content = buildString {
            appendLine(ask)
            if (request.isNotBlank()) appendLine("Player's request: ${request.trim()}")
            if (song != null) {
                appendLine("Song (GuitarNest format):")
                append(SongCodec.encode(song.copy(id = "")))
            }
        }
        return ChatRequest(model, listOf(ChatMessage("system", SYSTEM), ChatMessage("user", content)))
    }

    /** A follow-up message when the answer was not a valid song: the AI gets one chance to fix it. */
    fun repair(previous: ChatRequest, answer: String, problem: String): ChatRequest =
        previous.copy(messages = previous.messages + ChatMessage("assistant", answer) +
            ChatMessage("user", "This is not a valid GuitarNest song: $problem. Answer again with the corrected JSON object only."))

    /** Anthropic's API is used for api.anthropic.com; everything else is OpenAI-compatible. */
    fun styleFor(baseUrl: String): ApiStyle =
        if (baseUrl.substringAfter("://").substringBefore('/').substringBefore(':').endsWith("anthropic.com")) ApiStyle.ANTHROPIC else ApiStyle.OPENAI

    /** Path after the base URL. */
    fun path(style: ApiStyle): String = if (style == ApiStyle.ANTHROPIC) "/messages" else "/chat/completions"

    fun encode(request: ChatRequest, style: ApiStyle = ApiStyle.OPENAI): String = when (style) {
        ApiStyle.OPENAI -> json.encodeToString(ChatRequest.serializer(), request)
        ApiStyle.ANTHROPIC -> json.encodeToString(
            AnthropicRequest.serializer(),
            AnthropicRequest(
                model = request.model,
                maxTokens = MAX_TOKENS,
                system = request.messages.filter { it.role == "system" }.joinToString("\n\n") { it.content }.ifEmpty { null },
                messages = request.messages.filter { it.role != "system" },
                temperature = request.temperature,
            ),
        )
    }

    /** The assistant text from the server's response. */
    fun answerText(responseBody: String, style: ApiStyle = ApiStyle.OPENAI): String = try {
        val text = when (style) {
            ApiStyle.OPENAI -> json.decodeFromString(ChatResponse.serializer(), responseBody).choices.firstOrNull()?.message?.content
            ApiStyle.ANTHROPIC -> json.decodeFromString(AnthropicResponse.serializer(), responseBody).content
                .filter { it.type == "text" }.mapNotNull { it.text }.joinToString("").ifEmpty { null }
        }
        text ?: throw TabFormatException("The AI sent an empty answer")
    } catch (e: IllegalArgumentException) {
        throw TabFormatException("Unexpected answer from the AI server")
    }

    /** Enough for a long advanced tab. */
    private const val MAX_TOKENS = 16_000

    /** The song in the AI's answer, checked. [source] says who made it. */
    fun songFrom(answer: String, source: String, level: Level?): Song {
        val text = answer.trim().removePrefix("```json").removePrefix("```").removeSuffix("```")
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw TabFormatException("No tab in the AI answer")
        val song = SongCodec.decode(text.substring(start, end + 1))
        return song.copy(id = "", source = source, level = level ?: song.level)
    }
}
