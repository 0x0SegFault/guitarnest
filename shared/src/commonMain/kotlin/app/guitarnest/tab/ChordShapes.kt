package app.guitarnest.tab


/**
 * A way to play a chord: [frets] per string, lowest string first; -1 = not played, 0 = open.
 * Frets are counted from the capo, like everything in a tab.
 */
data class ChordShape(val frets: List<Int>) {
    /** Lowest fretted position, for drawing a 5-fret diagram ([firstFret] 1 = the nut is shown). */
    val firstFret: Int get() = frets.filter { it > 0 }.minOrNull()?.let { if (frets.maxOrNull()!! <= 4) 1 else it } ?: 1

    /**
     * A barre: the lowest fret held by one finger across strings from..to (indexes), or null.
     * Only when it is needed: at least three strings, no open string inside, more than three fretted notes
     * (so the open D and E minor shapes, held with separate fingers, have no barre).
     */
    val barre: Triple<Int, Int, Int>? get() {
        val fretted = frets.filter { it > 0 }
        val low = fretted.minOrNull() ?: return null
        val strings = frets.indices.filter { frets[it] == low }
        val from = strings.first()
        val to = strings.last()
        if (to - from < 2 || fretted.size <= 3) return null
        if ((from..to).any { frets[it] <= 0 }) return null
        return Triple(low, from, to)
    }

    /** "x32010", or "8-10-10-9-8-8" when a fret has two digits. */
    override fun toString(): String =
        if (frets.all { it < 10 }) frets.joinToString("") { if (it < 0) "x" else it.toString() }
        else frets.joinToString("-") { if (it < 0) "x" else it.toString() }

    companion object {
        fun parse(text: String): ChordShape? {
            val parts = if ('-' in text) text.split("-") else text.map { it.toString() }
            if (parts.size != 6) return null
            val frets = parts.map { p -> if (p == "x" || p == "X") -1 else p.toIntOrNull()?.takeIf { it in 0..24 } ?: return null }
            return ChordShape(frets)
        }
    }
}

/**
 * Chord diagrams computed from music theory (not copied from a chord book): every playable voicing of
 * a chord in a tuning, within four frets, with at most four fingers (a barre counts as one) and the
 * root (or the slash bass) as the lowest note. Best first: low positions, open strings, more strings.
 */
object ChordShapes {
    private val QUALITIES = linkedMapOf(
        "maj7" to listOf(0, 4, 7, 11), "m7b5" to listOf(0, 3, 6, 10), "dim7" to listOf(0, 3, 6, 9),
        "7sus4" to listOf(0, 5, 7, 10), "madd9" to listOf(0, 3, 7, 2), "add9" to listOf(0, 4, 7, 2),
        "sus2" to listOf(0, 2, 7), "sus4" to listOf(0, 5, 7), "sus" to listOf(0, 5, 7),
        "dim" to listOf(0, 3, 6), "aug" to listOf(0, 4, 8), "+" to listOf(0, 4, 8),
        "m9" to listOf(0, 3, 7, 10, 2), "m7" to listOf(0, 3, 7, 10), "m6" to listOf(0, 3, 7, 9),
        "9" to listOf(0, 4, 7, 10, 2), "7" to listOf(0, 4, 7, 10), "6" to listOf(0, 4, 7, 9),
        "5" to listOf(0, 7), "m" to listOf(0, 3, 7), "" to listOf(0, 4, 7),
    )

    /** Root pitch class, chord tones (pitch classes) and bass pitch class, or null if the name is unknown. */
    fun parse(name: String): Triple<Int, Set<Int>, Int>? {
        val (main, slash) = name.trim().split("/", limit = 2).let { it[0] to it.getOrNull(1) }
        val root = pitchClass(main) ?: return null
        val rootLength = if (main.length > 1 && main[1] in "#b♯♭") 2 else 1
        val suffix = main.substring(rootLength).replace("min", "m").replace("maj", "maj").let { if (it == "M") "" else it }
        val intervals = QUALITIES[suffix] ?: return null
        val tones = intervals.map { (root + it) % 12 }.toSet()
        val bass = slash?.let { pitchClass(it) } ?: root
        return Triple(root, tones + bass, bass)
    }

    private fun pitchClass(text: String): Int? {
        val base = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)[text.firstOrNull()?.uppercaseChar()] ?: return null
        val shift = when (text.getOrNull(1)) { '#', '♯' -> 1; 'b', '♭' -> -1; else -> 0 }
        return (base + shift + 12) % 12
    }

    /** Up to [limit] voicings for [name] in [tuning] (open-string MIDI notes, lowest first). */
    fun voicings(name: String, tuning: List<Int> = STANDARD, limit: Int = 6): List<ChordShape> {
        val (root, tones, bass) = parse(name) ?: return emptyList()
        val thirdOrSus = tones.filter { it != root && it != (root + 7) % 12 }.toSet()
        val found = LinkedHashMap<String, Pair<ChordShape, Double>>()
        for (start in 0..12) {
            val low = maxOf(1, start)
            val high = low + 3
            val choices = tuning.map { open ->
                buildList {
                    add(-1)
                    if ((open % 12) in tones) add(0)
                    for (f in low..high) if (start > 0 || f <= 3) if ((open + f) % 12 in tones) add(f)
                }
            }
            val current = IntArray(6)
            fun walk(s: Int) {
                if (s == 6) { consider(current.toList(), tuning, root, tones, bass, thirdOrSus)?.let { (shape, score) ->
                    val key = shape.toString()
                    if (found[key]?.second?.let { score < it } != false) found[key] = shape to score
                }; return }
                for (f in choices[s]) { current[s] = f; walk(s + 1) }
            }
            walk(0)
        }
        // Best per neck position first, so the player sees the open shape and barre shapes up the neck.
        val sorted = found.values.sortedBy { it.second }
        val byPosition = sorted.groupBy { it.first.firstFret / 3 }.values.map { it.first() }.sortedBy { it.second }
        return (byPosition + sorted).map { it.first }.distinct().take(limit)
    }

    private fun consider(frets: List<Int>, tuning: List<Int>, root: Int, tones: Set<Int>, bass: Int, needed: Set<Int>): Pair<ChordShape, Double>? {
        val played = frets.indices.filter { frets[it] >= 0 }
        if (played.size < (if (tones.size <= 2) 2 else 4)) return null
        // Not-played strings only on the bass side, so the chord can be strummed.
        if (played.first() + played.size - 1 != played.last() || played.last() != 5) return null
        val notes = played.map { tuning[it] + frets[it] }
        if (notes.first() % 12 != bass) return null
        val classes = notes.map { it % 12 }.toSet()
        if (root !in classes || !classes.containsAll(needed.take(2).toSet())) return null
        if (tones.size >= 4 && classes.size < 4) return null
        val fretted = played.filter { frets[it] > 0 }
        val shape = ChordShape(frets)
        val barre = shape.barre
        val fingers = if (barre != null) 1 + fretted.count { frets[it] > barre.first } else fretted.size
        if (fingers > 4) return null
        val top = fretted.maxOfOrNull { frets[it] } ?: 0
        val bottom = fretted.minOfOrNull { frets[it] } ?: 0
        if (top - bottom > 3) return null
        // Open strings with a hand high on the neck make shapes that are hard to read and rarely used.
        if (top >= 5 && played.any { frets[it] == 0 }) return null
        // Lower is better: position, fingers, stretch, barres, strings not played. Open strings help in low
        // positions; high on the neck they make odd shapes that players rarely use.
        val open = played.count { frets[it] == 0 }
        val score = bottom * 1.0 + fingers * 0.8 + (top - bottom) * 0.7 + (if (barre != null) 1.5 else 0.0) +
            (6 - played.size) * 0.9 + open * (if (bottom > 4) 1.5 else -0.6)
        return shape to score
    }

    val STANDARD = listOf(40, 45, 50, 55, 59, 64)

    /** A song's chosen shape for [name], else the best computed one. */
    fun shapeFor(song: Song, name: String): ChordShape? {
        song.shapes[name]?.let { ChordShape.parse(it) }?.let { return it }
        return voicings(name, song.tuningNotes()?.strings ?: STANDARD, 1).firstOrNull()
    }

}
