package app.guitarnest.tuner

import kotlin.test.Test
import kotlin.test.assertEquals

class PitchSmootherTest {
    @Test
    fun medianRemovesSpikes() {
        val s = PitchSmoother()
        listOf(110.0, 110.2, 109.9, 116.0).forEach { s.add(it) }
        assertEquals(110.2, s.add(110.3))
    }

    @Test
    fun noteChangeResets() {
        val s = PitchSmoother()
        repeat(4) { s.add(110.0) }
        assertEquals(146.8, s.add(146.8))
    }
}
