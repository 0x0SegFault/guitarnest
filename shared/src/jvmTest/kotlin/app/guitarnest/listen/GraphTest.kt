package app.guitarnest.listen

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/** The Kotlin interpreter must give the same result as the official ONNX runtime (reference made with onnxruntime). */
class GraphTest {
    private val model = File("../models/basic-pitch.gnm").readBytes()

    @Test
    fun matchesOnnxRuntime() {
        val n = BasicPitch.WINDOW
        val x = FloatArray(n) { i ->
            val env = exp(-i / 22050.0)
            (env * (0.3 * sin(2 * PI * 196.0 * i / 22050) + 0.2 * sin(2 * PI * 246.94 * i / 22050) + 0.2 * sin(2 * PI * 293.66 * i / 22050))).toFloat()
        }
        val graph = Graph(model)
        graph.run(Tensor(intArrayOf(1, n, 1), x)) // warm-up
        val started = System.nanoTime()
        val out = graph.run(Tensor(intArrayOf(1, n, 1), x))
        val ms = (System.nanoTime() - started) / 1_000_000
        val ref = ByteBuffer.wrap(javaClass.getResource("/basic-pitch-reference.bin")!!.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val expected = FloatArray(ref.remaining()).also { ref.get(it) }
        val actual = out.getValue(BasicPitch.NOTE).data + out.getValue(BasicPitch.ONSET).data
        var worst = 0f
        for (k in expected.indices) worst = maxOf(worst, abs(expected[k] - actual[k]))
        println("Basic Pitch window: $ms ms, max difference $worst")
        assertTrue(worst < 1e-3f, "max difference $worst")
    }
}
