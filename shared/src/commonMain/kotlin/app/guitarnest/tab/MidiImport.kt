package app.guitarnest.tab

import app.guitarnest.listen.Analysis
import app.guitarnest.listen.Fingering
import app.guitarnest.listen.HeardNote
import app.guitarnest.tuner.Tuning
import kotlin.math.roundToInt

/**
 * Standard MIDI files (format 0 and 1) to a guitar tab: the notes are placed on strings and frets
 * (standard tuning), chords are named per beat, bars follow the file's tempo and time signature.
 * Useful for open-licence music, for example the Mutopia Project (public domain and Creative Commons).
 */
object MidiImport {
    private data class Event(val tick: Long, val kind: Int, val a: Int, val b: Int, val meta: Int = -1, val data: ByteArray = ByteArray(0))

    fun isMidi(bytes: ByteArray): Boolean = bytes.size > 14 && bytes.decodeToString(0, 4) == "MThd"

    fun import(bytes: ByteArray, fallbackTitle: String, source: String = "Imported file"): Song {
        if (!isMidi(bytes)) throw TabFormatException("Not a MIDI file")
        val reader = Reader(bytes)
        reader.skip(4)
        val headerLength = reader.u32()
        val format = reader.u16()
        val tracks = reader.u16()
        val division = reader.u16()
        if (division and 0x8000 != 0) throw TabFormatException("MIDI files with SMPTE time are not supported")
        if (format > 1) throw TabFormatException("MIDI format $format is not supported")
        reader.skip(headerLength - 6)
        val events = mutableListOf<Event>()
        repeat(tracks) {
            if (reader.remaining() < 8) return@repeat
            val id = reader.text(4)
            val length = reader.u32()
            if (id != "MTrk") { reader.skip(length); return@repeat }
            events += readTrack(reader.slice(length))
        }
        events.sortBy { it.tick }

        // Tempo map (microseconds per quarter note), time signature, title.
        var usPerQuarter = 500_000
        var beatsPerBar = 4
        var beatUnit = 4
        var title: String? = null
        events.firstOrNull { it.meta == 0x51 }?.let { usPerQuarter = ((it.data[0].toInt() and 0xFF) shl 16) or ((it.data[1].toInt() and 0xFF) shl 8) or (it.data[2].toInt() and 0xFF) }
        events.firstOrNull { it.meta == 0x58 }?.let { beatsPerBar = it.data[0].toInt(); beatUnit = 1 shl it.data[1].toInt() }
        events.firstOrNull { it.meta == 0x03 && it.data.isNotEmpty() }?.let { title = it.data.decodeToString().trim().ifBlank { null } }
        val quarterBeats = (beatsPerBar * 4 / beatUnit).coerceIn(1, 12)
        val secondsPerTick = usPerQuarter / 1_000_000.0 / division

        // Notes: note-on (velocity > 0) until the matching note-off; drums (channel 10) are left out.
        val open = mutableMapOf<Pair<Int, Int>, Event>()
        val notes = mutableListOf<Pair<Event, Long>>()
        for (e in events) {
            if (e.kind != 0x80 && e.kind != 0x90) continue
            val channel = e.a shr 8
            if (channel == 9) continue
            val key = channel to (e.a and 0xFF)
            if (e.kind == 0x90 && e.b > 0) open[key] = e
            else open.remove(key)?.let { start -> notes += start to e.tick }
        }
        if (notes.isEmpty()) throw TabFormatException("No notes in this MIDI file")
        val heard = notes.map { (start, end) ->
            HeardNote(start.tick * secondsPerTick, start.a and 0xFF, (end - start.tick) * secondsPerTick, 0.9f)
        }.filter { it.midi in 28..100 }

        val placed = Fingering.place(heard, Tuning.STANDARD.strings, 0).second
        val beatSeconds = usPerQuarter / 1_000_000.0
        val lastBeat = heard.maxOf { it.time + it.duration } / beatSeconds
        val barCount = (lastBeat / quarterBeats).toInt() + 1
        val grid = Analysis.Grid(60.0 / beatSeconds, 0.0, quarterBeats)
        val perBeat = Analysis.chordsPerBeat(heard, grid, barCount * quarterBeats)
        val bars = (0 until barCount).map { b ->
            var last: String? = null
            val chords = (0 until quarterBeats).mapNotNull { i ->
                perBeat[b * quarterBeats + i]?.takeIf { it != last }?.also { last = it }?.let { ChordMark(i.toDouble(), it) }
            }
            val beats = placed.mapNotNull { p ->
                val at = ((p.note.time / beatSeconds - b * quarterBeats) * 4).roundToInt() / 4.0
                if (at < 0 || at >= quarterBeats) null else at to TabNote(6 - p.string, p.fret)
            }.groupBy({ it.first }, { it.second }).entries.sortedBy { it.key }
                .map { (at, ns) -> Beat(at, ns.distinctBy { it.string }.sortedBy { it.string }) }
            Bar(chords = chords, beats = beats)
        }
        return Song(
            title = title ?: fallbackTitle.ifBlank { "MIDI file" }, source = source, level = Level.ADVANCED,
            tempo = (60.0 / beatSeconds).roundToInt().coerceIn(20, 300), beatsPerBar = quarterBeats,
            sections = bars.chunked(8).mapIndexed { i, b -> Section("Part ${i + 1}", b) },
            notes = "Imported from MIDI: strings and frets were chosen by GuitarNest. Check the fingering.",
        )
    }

    private fun readTrack(r: Reader): List<Event> {
        val out = mutableListOf<Event>()
        var tick = 0L
        var status = 0
        while (r.remaining() > 0) {
            tick += r.varLen()
            var first = r.u8()
            if (first and 0x80 != 0) { status = first; if (status < 0xF0) first = r.u8() } else if (status == 0) break
            when {
                status == 0xFF -> {
                    val type = r.u8()
                    val data = r.bytes(r.varLen().toInt())
                    out += Event(tick, 0xFF, 0, 0, type, data)
                }
                status == 0xF0 || status == 0xF7 -> r.skip(r.varLen().toInt())
                else -> {
                    val kind = status and 0xF0
                    val channel = status and 0x0F
                    val data2 = if (kind == 0xC0 || kind == 0xD0) 0 else r.u8()
                    if (kind == 0x80 || kind == 0x90) out += Event(tick, kind, (channel shl 8) or first, data2)
                }
            }
        }
        return out
    }

    private class Reader(private val b: ByteArray, private var p: Int = 0, private val end: Int = b.size) {
        fun remaining() = end - p
        fun u8(): Int { if (p >= end) throw TabFormatException("Broken MIDI file"); return b[p++].toInt() and 0xFF }
        fun u16() = (u8() shl 8) or u8()
        fun u32() = (u16() shl 16) or u16()
        fun skip(n: Int) { p += n.coerceAtLeast(0); if (p > end) throw TabFormatException("Broken MIDI file") }
        fun text(n: Int) = bytes(n).decodeToString()
        fun bytes(n: Int): ByteArray { if (p + n > end) throw TabFormatException("Broken MIDI file"); return b.copyOfRange(p, p + n).also { p += n } }
        fun slice(n: Int): Reader { if (p + n > end) throw TabFormatException("Broken MIDI file"); return Reader(b, p, p + n).also { p += n } }
        fun varLen(): Long {
            var v = 0L
            repeat(4) {
                val c = u8()
                v = (v shl 7) or (c and 0x7F).toLong()
                if (c and 0x80 == 0) return v
            }
            return v
        }
    }
}
