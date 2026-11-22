package app.guitarnest.practice

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MetronomeTest {
    @Test
    fun clicksAt120() {
        val t = ClickTrack(120.0, 4, sampleRate = 48_000)
        val clicks = t.clicksIn(0, 48_000 * 2)
        assertEquals(listOf(0L, 24_000, 48_000, 72_000), clicks.map { it.sample })
        assertEquals(listOf(ClickKind.BAR, ClickKind.BEAT, ClickKind.BEAT, ClickKind.BEAT), clicks.map { it.kind })
    }

    @Test
    fun noDriftAfterAnHour() {
        // 97 BPM does not divide 48 kHz evenly; the hour-mark beat must still be exact.
        val t = ClickTrack(97.0, 4)
        val beatsInHour = 97L * 60
        val hour = 48_000L * 3600
        val click = t.clicksIn(hour - 10, hour + 10).single()
        assertTrue(abs(click.sample - hour) <= 1, "${click.sample}")
        assertEquals(beatsInHour % 4 == 0L, click.kind == ClickKind.BAR)
    }

    @Test
    fun windowsDoNotLoseOrRepeatClicks() {
        val t = ClickTrack(133.0, 3, subdivisions = 2)
        val whole = t.clicksIn(0, 500_000)
        val pieces = (0 until 500_000 step 777).flatMap { t.clicksIn(it.toLong(), minOf(it + 777L, 500_000)) }
        assertEquals(whole, pieces)
        assertEquals(ClickKind.SUBDIVISION, whole[1].kind)
        assertEquals(ClickKind.BAR, whole[6].kind)
    }

    @Test
    fun positions() {
        val t = ClickTrack(60.0, 4, sampleRate = 1000)
        assertEquals(Position(0, 0, 0, 0.0), t.positionAt(0))
        assertEquals(Position(1, 1, 3, 0.5), t.positionAt(5_500))
    }

    @Test
    fun tapTempo() {
        val tap = TapTempo()
        assertNull(tap.tap(0)); assertNull(tap.tap(500))
        assertEquals(120, tap.tap(1000))
        assertEquals(120, tap.tap(1560)) // one late tap
        assertEquals(120, tap.tap(2000))
        assertNull(tap.tap(10_000)) // long pause starts again
    }

    @Test
    fun speedTrainer() {
        val s = SpeedTrainer(60, 80, step = 5, barsPerStep = 4)
        assertEquals(listOf(60, 60, 65, 70, 75, 80, 80), listOf(0, 3, 4, 8, 12, 16, 40).map(s::tempoForBar))
    }
}
