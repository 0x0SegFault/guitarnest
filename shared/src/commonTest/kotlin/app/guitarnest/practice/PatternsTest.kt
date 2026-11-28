package app.guitarnest.practice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PatternsTest {
    private fun accents(id: String) = PRESET_PATTERNS.first { it.id == id }.steps.mapIndexedNotNull { i, c -> if (c == 'R') i + 1 else null }

    @Test
    fun flamencoCompasAccentsAreOnTheRightBeats() {
        assertEquals(listOf(3, 6, 8, 10, 12), accents("solea"))
        // Bulerías counts from 12: beat 1 of the string is the 12.
        assertEquals(listOf(1, 4, 7, 9, 11), accents("bulerias"))
        assertEquals(listOf(1, 3, 5, 8, 11), accents("seguiriya"))
        assertEquals(listOf(1, 4), accents("tanguillo"))
    }

    @Test
    fun everyPatternFillsWholeBeats() {
        for (p in PRESET_PATTERNS) {
            assertEquals(0, p.steps.length % p.perBeat, p.id)
            assertTrue(p.beats in setOf(3, 4, 6, 12), "${p.id}: ${p.beats} beats")
        }
        assertEquals(PRESET_PATTERNS.size, PRESET_PATTERNS.map { it.id }.toSet().size)
    }
}
