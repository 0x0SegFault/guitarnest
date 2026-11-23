package app.guitarnest.listen

import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AcoustIdTest {
    @Test
    fun parsesBestMatchFirst() {
        val body = """{"status":"ok","results":[
            {"id":"a","score":0.62,"recordings":[{"title":"Other Song","artists":[{"name":"B"}]}]},
            {"id":"b","score":0.97,"recordings":[
                {"title":"Night Walk","duration":201.4,"artists":[{"name":"Ana","joinphrase":" & "},{"name":"Leo"}],
                 "releasegroups":[{"title":"Live","type":"Single"},{"title":"Roads","type":"Album"}]},
                {"title":"night walk","artists":[{"name":"Ana","joinphrase":" & "},{"name":"Leo"}]},
                {"artists":[]}]},
            {"id":"c","score":0.2,"recordings":[{"title":"Noise","artists":[]}]}]}"""
        val found = AcoustId.parse(body)
        assertEquals(listOf("Night Walk", "Other Song"), found.map { it.title })
        assertEquals("Ana & Leo", found[0].artist)
        assertEquals("Roads", found[0].album)
        assertEquals(201, found[0].seconds)
    }

    @Test
    fun noMatchIsEmpty() {
        assertTrue(AcoustId.parse("""{"status":"ok","results":[]}""").isEmpty())
    }

    @Test
    fun errorAnswerThrows() {
        val e = assertFailsWith<IllegalStateException> { AcoustId.parse("""{"status":"error","error":{"code":4,"message":"invalid API key"}}""") }
        assertEquals("invalid API key", e.message)
    }

    @Test
    fun formSendsOnlyFingerprintAndLength() {
        assertEquals("client=k&format=json&meta=recordings+releasegroups&duration=31&fingerprint=AQAB", AcoustId.form("AQAB", 31, "k"))
    }

    @Test
    fun shortAudioHasNoFingerprint() {
        assertNull(AcoustId.fingerprint(FloatArray(22050 * 5), 22050))
        val fp = AcoustId.fingerprint(FloatArray(22050 * 12) { sin(it * 0.05).toFloat() * 0.3f }, 22050)
        assertEquals(12, fp?.second)
        assertTrue(fp!!.first.startsWith("AQ"))
    }
}
