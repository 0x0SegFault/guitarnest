package app.guitarnest.net

import app.guitarnest.ai.AiTabMaker
import app.guitarnest.ai.AiTask
import app.guitarnest.ai.ApiStyle
import app.guitarnest.tab.Level
import app.guitarnest.tab.Song
import app.guitarnest.tab.TabFormatException
import app.guitarnest.ai.ChatRequest
import app.guitarnest.data.AiConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class AiException(message: String) : Exception(message)

/** Asks the AI for a song; an invalid answer is sent back once with the problem, so the AI can fix it. */
suspend fun AiClient.makeSong(connection: AiConnection, task: AiTask, level: Level, request: String, song: Song?): Song {
    var chat = AiTabMaker.request(connection.model, task, level, request, song)
    var answer = chat(connection, chat)
    val source = "AI (${connection.model})"
    val keepLevel = level.takeIf { task != AiTask.STRUMMING }
    return try {
        AiTabMaker.songFrom(answer, source, keepLevel)
    } catch (e: TabFormatException) {
        chat = AiTabMaker.repair(chat, answer, e.message ?: "invalid")
        answer = chat(connection, chat)
        AiTabMaker.songFrom(answer, source, keepLevel)
    }
}

/**
 * Calls the player's AI: Anthropic's Messages API for api.anthropic.com, else an OpenAI-compatible
 * chat endpoint ({baseUrl}/chat/completions): OpenAI, Mistral, OpenRouter, LiteLLM, Ollama, LM Studio, vLLM, llama.cpp...
 */
object AiClient {
    suspend fun chat(connection: AiConnection, request: ChatRequest): String = withContext(Dispatchers.IO) {
        val style = AiTabMaker.styleFor(connection.baseUrl)
        val url = URL(connection.baseUrl.trimEnd('/') + AiTabMaker.path(style))
        if (url.protocol !in setOf("http", "https")) throw AiException("The address must start with https:// or http://")
        val http = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 180_000 // local models can be slow
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "GuitarNest")
            if (style == ApiStyle.ANTHROPIC) {
                setRequestProperty("x-api-key", connection.apiKey)
                setRequestProperty("anthropic-version", "2023-06-01")
            } else if (connection.apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer ${connection.apiKey}")
            }
        }
        try {
            http.outputStream.use { it.write(AiTabMaker.encode(request, style).toByteArray()) }
            val code = http.responseCode
            val body = (if (code in 200..299) http.inputStream else http.errorStream)?.use { it.readBytes().decodeToString() } ?: ""
            when (code) {
                in 200..299 -> AiTabMaker.answerText(body, style)
                401, 403 -> throw AiException("The AI server refused the API key ($code)")
                404 -> throw AiException("Not found ($code): check the address (it often ends in /v1) and the model name")
                else -> throw AiException("The AI server answered $code: ${body.take(200)}")
            }
        } catch (e: IOException) {
            throw AiException("Cannot reach the AI server: ${e.message}")
        } finally {
            http.disconnect()
        }
    }
}
