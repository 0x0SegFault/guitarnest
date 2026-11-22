package app.guitarnest.listen

/** A note heard in the audio. [strength] is the onset confidence (0..1). */
data class HeardNote(val time: Double, val midi: Int, val duration: Double, val strength: Float)

/**
 * Notes from audio with Basic Pitch (Bittner et al. 2022, arXiv:2203.09893), run by [Graph].
 * Same windowing and note rules as ai/gntab/transcribe.py, so phone, server and research agree.
 */
class BasicPitch(model: ByteArray) {
    private val graph = Graph(model)

    /** Number of 2-second windows for [samples] of 22050 Hz audio, for progress bars. */
    fun windowCount(samples: Int): Int = (samples + OVERLAP / 2 + STEP - 1) / STEP

    /** Frame-wise note and onset activations, 88 bins (MIDI 21-108) per frame. [progress] gets (done, total). */
    fun activations(audio: FloatArray, progress: (Int, Int) -> Unit = { _, _ -> }): Pair<Array<FloatArray>, Array<FloatArray>> {
        val padded = FloatArray(OVERLAP / 2) + audio
        val frames = (audio.size.toLong() * FPS_NUM / (SAMPLE_RATE.toLong() * FPS_DEN)).toInt()
        val note = ArrayList<FloatArray>(frames + FRAMES)
        val onset = ArrayList<FloatArray>(frames + FRAMES)
        val total = windowCount(audio.size)
        var start = 0
        var done = 0
        while (start < padded.size) {
            val window = FloatArray(WINDOW)
            padded.copyInto(window, 0, start, minOf(padded.size, start + WINDOW))
            val out = graph.run(Tensor(intArrayOf(1, WINDOW, 1), window))
            val n = out.getValue(NOTE).data
            val o = out.getValue(ONSET).data
            for (f in HALF until FRAMES - HALF) {
                note += n.copyOfRange(f * BINS, (f + 1) * BINS)
                onset += o.copyOfRange(f * BINS, (f + 1) * BINS)
            }
            start += STEP
            progress(++done, total)
        }
        return note.take(frames).toTypedArray() to onset.take(frames).toTypedArray()
    }

    fun notes(audio: FloatArray, progress: (Int, Int) -> Unit = { _, _ -> }): List<HeardNote> {
        val (frame, onset) = activations(audio, progress)
        return extract(frame, onset)
    }

    companion object {
        /** Output names of the Basic Pitch network (the order in the file is onset, note, contour). */
        const val NOTE = "StatefulPartitionedCall:1"
        const val ONSET = "StatefulPartitionedCall:2"
        const val SAMPLE_RATE = 22050
        private const val HOP = 256
        const val WINDOW = SAMPLE_RATE * 2 - HOP
        private const val FRAMES = 172
        private const val OVERLAP_FRAMES = 30
        private const val HALF = OVERLAP_FRAMES / 2
        private const val OVERLAP = OVERLAP_FRAMES * HOP
        // Step by the frames kept per window, so note times do not drift (see research/transcription/README.md).
        private const val STEP = (FRAMES - OVERLAP_FRAMES) * HOP
        private const val BINS = 88
        // Frames per second = 22050 / 256 = 86.13...
        private const val FPS_NUM = 22050L
        private const val FPS_DEN = 256L
        const val FPS = SAMPLE_RATE.toDouble() / HOP

        /** Onset peaks start notes; a note lasts while its frame activation stays high. */
        fun extract(frame: Array<FloatArray>, onset: Array<FloatArray>, onsetThreshold: Float = 0.5f, frameThreshold: Float = 0.3f, minFrames: Int = 6): List<HeardNote> {
            val out = mutableListOf<HeardNote>()
            for (b in 0 until BINS) {
                for (p in 1 until onset.size - 1) {
                    val c = onset[p][b]
                    if (c <= onsetThreshold || c < onset[p - 1][b] || c <= onset[p + 1][b]) continue
                    var end = p
                    while (end + 1 < frame.size && frame[end + 1][b] > frameThreshold) end++
                    if (end - p < minFrames) continue
                    // A ringing note shows weak onsets when other notes start: keep only real re-plucks.
                    val before = frame[maxOf(0, p - 4)][b]
                    val rise = frame[minOf(frame.size - 1, p + 3)][b] - before
                    if (before > 0.5f && c < 0.76f && rise < 0.17f) continue
                    val a = onset[p - 1][b]; val d = onset[p + 1][b]
                    val den = a - 2 * c + d
                    val shift = if (den != 0f) 0.5 * (a - d) / den else 0.0
                    out += HeardNote((p + shift) / FPS, b + 21, (end - p) / FPS, c)
                }
            }
            out.sortWith(compareBy({ it.midi }, { it.time }))
            val kept = mutableListOf<HeardNote>()
            for (n in out) {
                val last = kept.lastOrNull()
                if (last != null && last.midi == n.midi && n.time - last.time < 0.06) {
                    if (n.strength > last.strength) kept[kept.size - 1] = n
                    continue
                }
                kept += n
            }
            return kept.sortedWith(compareBy({ it.time }, { it.midi }))
        }
    }
}
