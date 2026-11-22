package app.guitarnest.data

import android.content.Context
import android.util.AtomicFile
import app.guitarnest.tab.Song
import app.guitarnest.tab.MusicXmlImport
import app.guitarnest.tab.SongCodec
import app.guitarnest.tab.TabFormatException
import app.guitarnest.data.BackupContent
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

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

    /**
     * Imports a .gnt, MusicXML (.musicxml, .xml) or compressed MusicXML (.mxl) file.
     * A song with the same id is kept as a copy, not overwritten.
     */
    fun import(bytes: ByteArray): Song {
        val song = when {
            bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() -> MusicXmlImport.import(unzipMusicXml(bytes), IMPORTED)
            bytes.decodeToString().trimStart().startsWith("<") -> MusicXmlImport.import(bytes.decodeToString(), IMPORTED)
            else -> SongCodec.decode(bytes.decodeToString())
        }
        val existing = song.id.isNotBlank() && File(dir, song.id + EXT).exists()
        return save(if (existing || song.id.isBlank()) song.copy(id = "") else song)
    }

    /** The score inside a .mxl file: META-INF/container.xml names it. Size-limited against zip bombs. */
    private fun unzipMusicXml(bytes: ByteArray): String {
        val entries = mutableMapOf<String, String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var total = 0L
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val data = zip.readBytes()
                total += data.size
                if (total > MAX_UNZIPPED) throw TabFormatException("File too large")
                if (entry.name.endsWith(".xml") || entry.name.endsWith(".musicxml")) entries[entry.name] = data.decodeToString()
            }
        }
        val container = entries["META-INF/container.xml"]
        val main = container?.let { Regex("full-path=\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
        return entries[main] ?: entries.entries.firstOrNull { !it.key.startsWith("META-INF/") }?.value
            ?: throw TabFormatException("No score in this .mxl file")
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

    /** All songs and the practice history, for a backup. */
    fun backupContent(): BackupContent = BackupContent(songs = all(), practice = practiceLog())

    /** Restores a backup by merging. Returns the number of songs restored. */
    fun restore(content: BackupContent): Int {
        content.songs.forEach { save(it) }
        write(logFile, json.encodeToString(BackupContent.merge(practiceLog(), content.practice)))
        return content.songs.size
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
        const val IMPORTED = "Imported file"
        private const val MAX_UNZIPPED = 20L * 1024 * 1024
    }
}
