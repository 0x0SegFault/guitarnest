package app.guitarnest.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportsTest {
    @Test
    fun sha256KnownVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256.hex(ByteArray(0)))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.hex("abc".encodeToByteArray()))
        assertEquals("248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()))
    }

    @Test
    fun solvesTheServicePuzzle() {
        val salt = "a1b2c3?expires=9999999999"
        val challenge = Reports.Challenge("SHA-256", Sha256.hex((salt + 4321).encodeToByteArray()), 50000, salt, "sig")
        val proof = Reports.solve(challenge)!!
        assertEquals(4321, proof.number)
        val body = Reports.body("bug", "Title here", "Details here", "0.1.1", "Pixel", proof)
        assertTrue("\"number\":4321" in body && "\"kind\":\"bug\"" in body)
    }

    @Test
    fun githubLinkIsEncoded() {
        val url = Reports.githubUrl("feature", "Chords & more", "Line 1\nLine 2", "0.1.1", "Pixel 7")
        assertTrue(url.startsWith(Reports.GITHUB_NEW_ISSUE + "?labels=enhancement&title=Chords%20%26%20more&body=Line%201%0ALine%202"), url)
    }

    @Test
    fun crashIsAnonymised() {
        val trace = """GuitarNest 0.1.1, Android 15
java.lang.IllegalStateException: Song "My secret song" not found
	at app.guitarnest.ui.SongScreen.open(SongScreen.kt:42)
Caused by: java.io.IOException: /data/user/0/io.github/files/songs/abc.gnt: denied
	at java.io.FileInputStream.open(FileInputStream.java:1)
	... 12 more"""
        val clean = Reports.anonymizeCrash(trace)
        assertTrue("secret" !in clean && "/data/" !in clean, clean)
        assertTrue("java.lang.IllegalStateException" in clean && "Caused by: java.io.IOException" in clean)
        assertTrue("at app.guitarnest.ui.SongScreen.open(SongScreen.kt:42)" in clean)
        assertEquals("Crash: IllegalStateException", Reports.crashTitle(trace))
    }

    @Test
    fun previewMatchesTheIssueAndSentListRoundTrips() {
        val p = Reports.preview("bug", " Closes ", "When I save.", "0.1.1", "")
        assertTrue(p.startsWith("Bug: Closes\n\nWhen I save.") && p.endsWith("App version: 0.1.1\nDevice: not given\n\n" + Reports.FOOTER), p)
        val list = listOf(Reports.Sent("0123456789abcdef0123456789abcdef", "bug", "Closes", 20000))
        assertEquals(list, Reports.decodeSent(Reports.encodeSent(list)))
        assertEquals(Reports.Status("published", "https://github.com/o/r/issues/7"), Reports.parseStatus("""{"status":"published","public":"https://github.com/o/r/issues/7"}"""))
        assertEquals("unknown", Reports.parseStatus("<html>").status)
    }
}
