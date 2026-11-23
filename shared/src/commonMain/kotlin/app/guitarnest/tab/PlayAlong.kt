package app.guitarnest.tab

/** Chord timing during play along, shared by Android and iOS. */
object PlayAlong {
    /** Chord names in order of first use, for the chord diagrams of a song. */
    fun chordsInSong(song: Song): List<String> =
        song.sections.flatMap { s -> s.bars.flatMap { b -> b.chords.map { it.name } } }.distinct()

    /** The chord that sounds at [beat] of bar [index]: the last chord mark at or before it, else from earlier bars. */
    fun chordAt(order: List<PlayedBar>, index: Int, beat: Int): String? {
        if (order.isEmpty()) return null
        var i = index.coerceIn(0, order.size - 1)
        order[i].content.chords.lastOrNull { it.beat <= beat }?.let { return it.name }
        while (--i >= 0) order[i].content.chords.lastOrNull()?.let { return it.name }
        return null
    }

    /** The next different chord after the one at [beat] of bar [index], looking up to 8 bars ahead. */
    fun nextChord(order: List<PlayedBar>, index: Int, beat: Int): String? {
        if (order.isEmpty()) return null
        val now = chordAt(order, index, beat)
        val start = index.coerceIn(0, order.size - 1)
        for (i in start until minOf(order.size, start + 8)) {
            for (mark in order[i].content.chords) {
                if (i == start && mark.beat <= beat) continue
                if (mark.name != now) return mark.name
            }
        }
        return null
    }

    /** The song with another voicing chosen for [chord]: the next in [ChordShapes.voicings], wrapping around. */
    fun nextShape(song: Song, chord: String): Song {
        val list = ChordShapes.voicings(chord, song.tuningNotes()?.strings ?: ChordShapes.STANDARD)
        if (list.isEmpty()) return song
        val now = ChordShapes.shapeFor(song, chord)?.toString()
        val next = list[(list.indexOfFirst { it.toString() == now } + 1).mod(list.size)]
        return song.copy(shapes = song.shapes + (chord to next.toString()))
    }
}
