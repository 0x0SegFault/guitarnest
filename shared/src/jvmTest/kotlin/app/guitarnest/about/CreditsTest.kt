package app.guitarnest.about

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** CREDITS.md and the in-app list must stay the same. */
class CreditsTest {
    @Test
    fun creditsFileNamesEveryEntry() {
        val text = File("../CREDITS.md").readText()
        for (c in Credits.ALL) assertTrue("[${c.name}](${c.url})" in text, "CREDITS.md misses ${c.name}")
        assertTrue(Credits.ALL.all { it.url.startsWith("https://") })
    }
}
