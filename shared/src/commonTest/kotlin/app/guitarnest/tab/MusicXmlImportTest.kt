package app.guitarnest.tab

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MusicXmlImportTest {
    // Written for this test.
    private val xml = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE score-partwise PUBLIC "-//Recordare//DTD MusicXML 4.0 Partwise//EN" "http://www.musicxml.org/dtds/partwise.dtd">
<score-partwise version="4.0">
  <work><work-title>Test &amp; Riff</work-title></work>
  <identification><creator type="composer">Me</creator></identification>
  <part-list><score-part id="P1"><part-name>Guitar</part-name></score-part></part-list>
  <part id="P1">
    <measure number="1">
      <attributes>
        <divisions>2</divisions>
        <time><beats>4</beats><beat-type>4</beat-type></time>
        <staff-details><staff-lines>6</staff-lines>
          <staff-tuning line="1"><tuning-step>D</tuning-step><tuning-octave>2</tuning-octave></staff-tuning>
          <staff-tuning line="2"><tuning-step>A</tuning-step><tuning-octave>2</tuning-octave></staff-tuning>
          <staff-tuning line="3"><tuning-step>D</tuning-step><tuning-octave>3</tuning-octave></staff-tuning>
          <staff-tuning line="4"><tuning-step>G</tuning-step><tuning-octave>3</tuning-octave></staff-tuning>
          <staff-tuning line="5"><tuning-step>B</tuning-step><tuning-octave>3</tuning-octave></staff-tuning>
          <staff-tuning line="6"><tuning-step>E</tuning-step><tuning-octave>4</tuning-octave></staff-tuning>
          <capo>2</capo>
        </staff-details>
      </attributes>
      <direction><direction-type><rehearsal>Intro</rehearsal></direction-type><sound tempo="100"/></direction>
      <harmony><root><root-step>D</root-step></root><kind>power</kind></harmony>
      <note><pitch><step>E</step><octave>2</octave></pitch><duration>2</duration>
        <notations><technical><string>6</string><fret>0</fret></technical></notations></note>
      <note><chord/><pitch><step>B</step><octave>2</octave></pitch><duration>2</duration>
        <notations><technical><string>5</string><fret>0</fret></technical></notations></note>
      <note><rest/><duration>1</duration></note>
      <note><pitch><step>A</step><octave>3</octave></pitch><duration>1</duration>
        <notations><technical><hammer-on type="start">H</hammer-on><string>3</string><fret>0</fret></technical></notations></note>
      <harmony><root><root-step>F</root-step><root-alter>1</root-alter></root><kind>minor</kind></harmony>
      <note><pitch><step>B</step><octave>3</octave></pitch><duration>4</duration></note>
    </measure>
    <measure number="2">
      <direction><direction-type><rehearsal>Verse</rehearsal></direction-type></direction>
      <note><rest/><duration>8</duration></note>
    </measure>
  </part>
</score-partwise>"""

    @Test
    fun importsTabTuningCapoChordsAndSections() {
        val song = MusicXmlImport.import(xml, "Imported")
        assertEquals("Test & Riff", song.title)
        assertEquals("Me", song.artist)
        assertEquals("drop_d", song.tuning)
        assertEquals(2, song.capo)
        assertEquals(100, song.tempo)
        assertEquals(listOf("Intro", "Verse"), song.sections.map { it.name })
        val bar = song.sections[0].bars[0]
        assertEquals(listOf(ChordMark(0.0, "D5"), ChordMark(2.0, "F#m")), bar.chords)
        assertEquals(Beat(0.0, listOf(TabNote(5, 0), TabNote(6, 0))), bar.beats[0])
        assertEquals(Beat(1.5, listOf(TabNote(3, 0, "h"))), bar.beats[1])
        // B3 has no tab data: it goes on the free string with the lowest fret.
        assertEquals(2.0, bar.beats[2].at)
        assertTrue(SongCodec.validate(song).isEmpty(), SongCodec.validate(song).toString())
    }

    @Test
    fun refusesOtherFiles() {
        assertFailsWith<TabFormatException> { MusicXmlImport.import("<score-timewise/>") }
        assertFailsWith<TabFormatException> { MusicXmlImport.import("<score-partwise><part>") }
        assertFailsWith<TabFormatException> { MusicXmlImport.import("not xml") }
    }

    @Test
    fun xmlEntitiesAndCdata() {
        val n = Xml.parse("<a x='1 &lt; 2'><b><![CDATA[<raw>]]></b>&#233;&#x1F3B8;</a>")
        assertEquals("1 < 2", n.attributes["x"])
        assertEquals("<raw>", n.child("b")!!.text)
        assertEquals("é🎸", n.text)
    }
}
