package app.guitarnest.data

import android.content.Context
import android.util.AtomicFile
import app.guitarnest.tab.Song
import app.guitarnest.tab.SongCodec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** One practice session, kept on the phone. */
@Serializable
data class PracticeEntry(val songId: String, val day: Long, val seconds: Int, val bestTempo: Int)

/**
 * The tab library: one .gnt file per song in the app's private storage, plus a practice log.
 * Tabs are not secret, so they are not encrypted; they are excluded from cloud backup.
 */
class SongRepository(context: Context) {
    private val dir = File(context.filesDir, "songs").apply { mkdirs() }
    private val logFile = AtomicFile(File(context.filesDir, "practice.json"))
    private val json = Json { ignoreUnknownKeys = true }

    fun all(): List<Song> = dir.listFiles { f -> f.name.endsWith(EXT) }.orEmpty()
        .mapNotNull { f -> runCatching { SongCodec.decode(AtomicFile(f).readFully().decodeToString()) }.getOrNull() }
        .sortedBy { it.title.lowercase() }

    fun get(id: String): Song? = all().firstOrNull { it.id == id }

    /** Saves the song and returns it with its id. */
    fun save(song: Song): Song {
        val withId = if (song.id.isBlank()) song.copy(id = UUID.randomUUID().toString()) else song
        write(AtomicFile(File(dir, withId.id + EXT)), SongCodec.encode(withId))
        return withId
    }

    fun delete(id: String) {
        AtomicFile(File(dir, id + EXT)).delete()
    }

    /** Imports a .gnt text. A song with the same id is kept as a copy, not overwritten. */
    fun import(text: String): Song {
        val song = SongCodec.decode(text)
        val existing = song.id.isNotBlank() && File(dir, song.id + EXT).exists()
        return save(if (existing || song.id.isBlank()) song.copy(id = "") else song)
    }

    private val seededMark = File(context.filesDir, ".seeded")

    /** The example song is added once; if the player deletes it, it stays deleted. */
    fun seeded(): Boolean = seededMark.exists()

    fun markSeeded() {
        seededMark.createNewFile()
    }

    fun practiceLog(): List<PracticeEntry> = runCatching {
        json.decodeFromString<List<PracticeEntry>>(logFile.readFully().decodeToString())
    }.getOrDefault(emptyList())

    fun logPractice(entry: PracticeEntry) {
        if (entry.seconds < 10) return
        write(logFile, json.encodeToString(practiceLog() + entry))
    }

    private fun write(file: AtomicFile, text: String) {
        val out = file.startWrite()
        try {
            out.write(text.toByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }

    companion object {
        const val EXT = ".gnt"
    }
}
