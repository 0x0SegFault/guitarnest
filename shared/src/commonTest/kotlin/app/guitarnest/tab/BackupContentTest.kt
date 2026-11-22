package app.guitarnest.tab

import app.guitarnest.data.BackupContent
import app.guitarnest.data.PracticeEntry
import kotlin.test.Test
import kotlin.test.assertEquals

class BackupContentTest {
    @Test
    fun roundTripAndMerge() {
        val c = BackupContent(songs = listOf(Song(id = "a", title = "A")), practice = listOf(PracticeEntry("a", 20000, 60, 90)))
        assertEquals(c, BackupContent.decode(BackupContent.encode(c)))
        val merged = BackupContent.merge(c.practice, c.practice + PracticeEntry("a", 20001, 30, 80))
        assertEquals(2, merged.size)
    }
}
