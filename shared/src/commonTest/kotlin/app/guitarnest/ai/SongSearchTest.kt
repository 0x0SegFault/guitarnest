package app.guitarnest.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SongSearchTest {
    @Test
    fun urlIsEncoded() {
        assertEquals("https://musicbrainz.org/ws/2/recording?fmt=json&limit=15&query=Caf%C3%A9%20%26%20Blues", SongSearch.url(" Café & Blues "))
    }

    @Test
    fun parsesRecordings() {
        // Shape of a MusicBrainz answer; values made up for the test.
        val body = """{"created":"x","count":2,"recordings":[
          {"id":"1","title":"Song A","length":215000,"artist-credit":[{"name":"Band","joinphrase":" feat. "},{"name":"Singer"}],
           "releases":[{"title":"Live","date":"2005-01-01"},{"title":"First","date":"1999-05-02"}]},
          {"id":"2","title":"Song A","artist-credit":[{"name":"Band"}]},
          {"id":"3","title":"Song A","length":215500,"artist-credit":[{"name":"Band","joinphrase":" feat. "},{"name":"Singer"}],
           "releases":[{"title":"First","date":"1999"}]}]}"""
        val results = SongSearch.parse(body)
        assertEquals(2, results.size)
        assertEquals(SongInfo("Song A", "Band feat. Singer", "First", "1999", 215), results[0])
        assertTrue(results[1].seconds == null && results[1].album == "")
    }
}
