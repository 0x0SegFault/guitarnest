package app.guitarnest.data

import app.guitarnest.tab.Song
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One practice session, kept on the device. */
@Serializable
data class PracticeEntry(val songId: String, val day: Long, val seconds: Int, val bestTempo: Int)

/**
 * Content of a backup file (before encryption): all songs and the practice history.
 * Same JSON on Android and iOS, so a backup moves between platforms. The AI key is never included.
 */
@Serializable
data class BackupContent(val format: Int = 1, val songs: List<Song> = emptyList(), val practice: List<PracticeEntry> = emptyList()) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        fun encode(c: BackupContent): String = json.encodeToString(serializer(), c)
        fun decode(text: String): BackupContent = json.decodeFromString(serializer(), text)

        /** Restore merges: practice entries are added once. */
        fun merge(current: List<PracticeEntry>, incoming: List<PracticeEntry>): List<PracticeEntry> = (current + incoming).distinct()
    }
}
