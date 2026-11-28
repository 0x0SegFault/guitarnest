package app.guitarnest.scores

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FreeScoresTest {
    @Test
    fun theIndexIsComplete() {
        assertEquals(MutopiaData.COUNT, FreeScores.all.size)
        assertTrue(FreeScores.all.size > 100)
    }

    @Test
    fun everyScoreIsFreeAndDownloadable() {
        for (s in FreeScores.all) {
            assertTrue(s.licence == "Public domain" || s.licence.startsWith("CC BY"), "${s.title}: ${s.licence}")
            assertTrue(!s.licence.contains("NC"), "${s.title}: non-commercial licence")
            assertTrue(s.midiUrl.startsWith("https://www.mutopiaproject.org/ftp/") && s.midiUrl.endsWith(".mid"), s.midiUrl)
        }
    }

    @Test
    fun searchIgnoresAccentsAndCase() {
        assertTrue(FreeScores.search("lagrima").any { "Tárrega" in it.composer })
        assertTrue(FreeScores.search("SOR").isNotEmpty())
        assertTrue(FreeScores.search("giuliani op").all { "Giuliani" in it.composer })
        assertTrue(FreeScores.search("   ").isEmpty())
    }
}
