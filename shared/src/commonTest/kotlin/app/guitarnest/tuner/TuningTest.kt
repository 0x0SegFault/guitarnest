package app.guitarnest.tuner

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TuningTest {
    @Test
    fun presetsHaveSixStringsLowToHigh() {
        for (t in Tuning.PRESETS) {
            assertEquals(6, t.strings.size, t.id)
            assertEquals(t.strings.sorted(), t.strings, t.id)
        }
        assertEquals(Tuning.PRESETS.size, Tuning.PRESETS.map { it.id }.toSet().size)
    }

    @Test
    fun standardAndDropD() {
        assertEquals(listOf("E2", "A2", "D3", "G3", "B3", "E4"), Tuning.STANDARD.names)
        assertEquals("D2", Tuning.byId("drop_d")!!.names[0])
        assertEquals(listOf("E♭2", "A♭2", "D♭3", "G♭3", "B♭3", "E♭4"), Tuning.byId("half_step_down")!!.names)
        assertEquals("F♯3", Tuning.byId("open_d")!!.names[3])
    }

    @Test
    fun capoRaisesEveryString() {
        assertEquals(listOf(42, 47, 52, 57, 61, 66), Tuning.STANDARD.withCapo(2))
    }

    @Test
    fun nearestStringAndCents() {
        val t = Tuning.STANDARD
        val slightlyFlatA = Notes.frequencyOf(45) * 0.98
        assertEquals(1, t.nearestString(slightlyFlatA))
        assertTrue(abs(t.centsOff(1, slightlyFlatA) + 35.0) < 0.1)
        assertEquals(0, t.nearestString(70.0))
        assertEquals(5, t.nearestString(500.0))
    }
}
