package app.guitarnest.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Debug builds only: a synthetic string tone in place of the microphone, for screenshots and
 * on-device tests of the detector. Start with: adb shell am start ... --ef demo_hz 109.0
 */
class DemoSignal(private val sampleRate: Int, private val frequency: Double) {
    fun windows(windowSize: Int, hop: Int): Flow<FloatArray> = flow {
        var t = 0L
        val harmonics = doubleArrayOf(0.5, 1.0, 0.6, 0.35, 0.2)
        while (true) {
            emit(FloatArray(windowSize) { i ->
                val n = t + i
                val env = exp(-(n % (sampleRate * 3L)).toDouble() / sampleRate)
                (0.25 * env * harmonics.indices.sumOf { h ->
                    harmonics[h] * sin(2 * PI * frequency * (h + 1) * n / sampleRate)
                }).toFloat()
            })
            t += hop
            delay(1000L * hop / sampleRate)
        }
    }.flowOn(Dispatchers.Default)
}
