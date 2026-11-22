package app.guitarnest.listen

import app.guitarnest.tuner.Tuning
import kotlin.math.abs
import kotlin.math.roundToInt

/** A note placed on the neck. [string] 0 = lowest string; [fret] counted from the capo. */
data class Placed(val note: HeardNote, val string: Int, val fret: Int)

/**
 * Strings and frets for heard notes, and a guess of tuning and capo: the cheapest hand path over
 * all choices (optimum path idea of Sayegh 1989, doi:10.2307/3680014). Port of ai/gntab/fingering.py.
 */
object Fingering {
    private const val MAX_FRET = 15
    const val GROUP_GAP = 0.06

    /** Prior cost per tuning: common tunings first, so ambiguous cases pick the usual one. */
    private val PRIOR = mapOf(
        "standard" to 0.0, "drop_d" to 1.5, "half_step_down" to 2.0, "whole_step_down" to 3.0,
        "drop_c" to 3.5, "open_g" to 4.0, "open_d" to 4.5, "dadgad" to 4.5, "open_e" to 5.0,
    )

    fun groups(notes: List<HeardNote>): List<List<HeardNote>> {
        val out = mutableListOf<MutableList<HeardNote>>()
        for (n in notes.sortedBy { it.time }) {
            if (out.isNotEmpty() && n.time - out.last()[0].time < GROUP_GAP) out.last() += n else out += mutableListOf(n)
        }
        return out
    }

    private fun candidates(midi: Int, tuning: List<Int>, capo: Int) =
        (0 until 6).map { it to midi - tuning[it] - capo }.filter { it.second in 0..MAX_FRET }

    private fun shapeCost(choice: List<Pair<Int, Int>>): Pair<Double, Double?> {
        val fretted = choice.map { it.second }.filter { it > 0 }
        if (fretted.isEmpty()) return 0.0 to null
        val low = fretted.min(); val high = fretted.max(); val span = high - low
        val cost = span * 1.0 + (if (span > 4) 20.0 else 0.0) + 0.25 * low + 0.15 * fretted.size
        return cost to (low + high) / 2.0
    }

    private fun assignments(group: List<HeardNote>, tuning: List<Int>, capo: Int, limit: Int = 300): List<List<Pair<Int, Int>>>? {
        val options = group.map { candidates(it.midi, tuning, capo) }
        if (options.any { it.isEmpty() }) return null
        val out = mutableListOf<List<Pair<Int, Int>>>()
        fun walk(k: Int, chosen: List<Pair<Int, Int>>) {
            if (out.size >= limit) return
            if (k == options.size) { out += chosen; return }
            for (o in options[k]) if (chosen.none { it.first == o.first }) walk(k + 1, chosen + o)
        }
        walk(0, emptyList())
        return out.ifEmpty { null }
    }

    /** Cost and placed notes for one tuning and capo. Unplayable notes cost 10 each and are left out. */
    fun place(notes: List<HeardNote>, tuning: List<Int>, capo: Int): Pair<Double, List<Placed>> {
        var penalty = 0.0
        var states = mapOf<Double?, Pair<Double, List<Pair<List<HeardNote>, List<Pair<Int, Int>>>>>>(null to (0.0 to emptyList()))
        for (g in groups(notes)) {
            var group = g
            var options = assignments(group, tuning, capo)
            if (options == null) {
                val playable = group.filter { candidates(it.midi, tuning, capo).isNotEmpty() }
                penalty += 10.0 * (group.size - playable.size)
                group = playable
                options = if (group.isEmpty()) null else assignments(group, tuning, capo)
                if (options == null) continue
            }
            val next = HashMap<Double?, Pair<Double, List<Pair<List<HeardNote>, List<Pair<Int, Int>>>>>>()
            for (combo in options) {
                val (cost, position) = shapeCost(combo)
                for ((prev, value) in states) {
                    val move = if (prev == null || position == null) 0.0 else 0.5 * abs(position - prev)
                    val key = (position ?: prev)?.let { (it * 2).roundToInt() / 2.0 }
                    val total = value.first + cost + move
                    if (next[key]?.let { total < it.first } != false) next[key] = total to (value.second + (group to combo))
                }
            }
            states = next
        }
        val (cost, path) = states.values.minBy { it.first }
        return (cost + penalty) to path.flatMap { (g, combo) -> g.zip(combo).map { (n, sf) -> Placed(n, sf.first, sf.second) } }
    }

    data class Setup(val score: Double, val tuning: Tuning, val capo: Int, val placed: List<Placed>)

    /** Best tuning and capo (0-7). Small priors favour common tunings and no capo. */
    fun bestSetup(notes: List<HeardNote>): Setup = Tuning.PRESETS.flatMap { t ->
        (0..7).map { capo ->
            val (cost, placed) = place(notes, t.strings, capo)
            Setup(cost / maxOf(1, notes.size) * 10 + (PRIOR[t.id] ?: 5.0) + 1.5 * capo, t, capo, placed)
        }
    }.minBy { it.score }
}
