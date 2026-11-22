package app.guitarnest.listen

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/** A float tensor in row-major order. Integer tensors (shapes, indices) are stored as floats too. */
class Tensor(val shape: IntArray, val data: FloatArray) {
    init { require(data.size == shape.fold(1) { a, b -> a * b }) { "shape ${shape.toList()} does not match ${data.size} values" } }
    val rank get() = shape.size
    fun ints(): IntArray = IntArray(data.size) { data[it].toInt() }
}

/**
 * A small interpreter for the operations of the Basic Pitch network (ONNX opset 15 subset):
 * Conv, Pad, Slice, Reshape, Transpose, Concat, element-wise maths and reductions.
 * Pure Kotlin, so the same code runs on Android, iOS and in JVM tests, with no native runtime.
 * The model file is made by ai/tools/export_model.py.
 */
class Graph(bytes: ByteArray) {
    private class Node(val op: String, val inputs: List<String>, val outputs: List<String>, val attrs: JsonObject)

    private val nodes: List<Node>
    private val constants = HashMap<String, Tensor>()
    val inputName: String
    val outputNames: List<String>

    init {
        require(bytes.size > 8 && bytes.decodeToString(0, 4) == "GNM1") { "Not a GuitarNest model file" }
        val headerLength = le32(bytes, 4)
        val header = Json.parseToJsonElement(bytes.decodeToString(8, 8 + headerLength)).jsonObject
        val base = 8 + headerLength
        for ((name, info) in header.getValue("tensors").jsonObject) {
            val o = info.jsonObject
            val shape = o.getValue("shape").jsonArray.map { it.jsonPrimitive.int }.toIntArray()
            val offset = o.getValue("offset").jsonPrimitive.int
            val length = o.getValue("length").jsonPrimitive.int
            constants[name] = Tensor(shape, FloatArray(length) { Float.fromBits(le32(bytes, base + 4 * (offset + it))) })
        }
        nodes = header.getValue("nodes").jsonArray.map { n ->
            val o = n.jsonObject
            Node(
                o.getValue("op").jsonPrimitive.content,
                o.getValue("in").jsonArray.map { it.jsonPrimitive.content },
                o.getValue("out").jsonArray.map { it.jsonPrimitive.content },
                o.getValue("attrs").jsonObject,
            )
        }
        inputName = header.getValue("inputs").jsonArray.single().jsonPrimitive.content
        outputNames = header.getValue("outputs").jsonArray.map { it.jsonPrimitive.content }
    }

    fun run(input: Tensor): Map<String, Tensor> {
        val values = HashMap<String, Tensor>(constants)
        values[inputName] = input
        for (n in nodes) {
            val ins = n.inputs.map { if (it.isEmpty()) null else values[it] ?: error("Missing value $it for ${n.op}") }
            values[n.outputs[0]] = Ops.run(n.op, ins, n.attrs)
        }
        return outputNames.associateWith { values.getValue(it) }
    }

    private fun le32(b: ByteArray, i: Int): Int =
        (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8) or ((b[i + 2].toInt() and 0xFF) shl 16) or ((b[i + 3].toInt() and 0xFF) shl 24)
}

internal object Ops {
    fun run(op: String, i: List<Tensor?>, a: JsonObject): Tensor = when (op) {
        "Add" -> binary(i[0]!!, i[1]!!) { x, y -> x + y }
        "Sub" -> binary(i[0]!!, i[1]!!) { x, y -> x - y }
        "Mul" -> binary(i[0]!!, i[1]!!) { x, y -> x * y }
        "Div" -> binary(i[0]!!, i[1]!!) { x, y -> x / y }
        "Equal" -> binary(i[0]!!, i[1]!!) { x, y -> if (x == y) 1f else 0f }
        "Neg" -> unary(i[0]!!) { -it }
        "Sqrt" -> unary(i[0]!!) { sqrt(it) }
        "Log" -> unary(i[0]!!) { ln(it) }
        "Relu" -> unary(i[0]!!) { if (it > 0f) it else 0f }
        "Sigmoid" -> unary(i[0]!!) { (1.0 / (1.0 + exp(-it.toDouble()))).toFloat() }
        "Cast" -> if (a.int("to") in setOf(6, 7)) unary(i[0]!!) { it.toInt().toFloat() } else i[0]!!
        "Shape" -> Tensor(intArrayOf(i[0]!!.rank), FloatArray(i[0]!!.rank) { i[0]!!.shape[it].toFloat() })
        "Where" -> where(i[0]!!, i[1]!!, i[2]!!)
        "Reshape" -> reshape(i[0]!!, i[1]!!.ints())
        "Unsqueeze" -> unsqueeze(i[0]!!, i[1]!!.ints())
        "Transpose" -> transpose(i[0]!!, a.ints("perm"))
        "Concat" -> concat(i.map { it!! }, a.int("axis"))
        "Slice" -> slice(i[0]!!, i[1]!!.ints(), i[2]!!.ints(), i.getOrNull(3)?.ints(), i.getOrNull(4)?.ints())
        "Pad" -> pad(i[0]!!, i[1]!!.ints(), (a["mode"] as? JsonPrimitive)?.content ?: "constant", i.getOrNull(2)?.data?.firstOrNull() ?: 0f)
        "ReduceSum" -> reduce(i[0]!!, i.getOrNull(1)?.ints() ?: a.intsOrNull("axes"), a.int("keepdims", 1) == 1, 0f) { x, y -> x + y }
        "ReduceMin" -> reduce(i[0]!!, a.intsOrNull("axes"), a.int("keepdims", 1) == 1, Float.MAX_VALUE) { x, y -> if (y < x) y else x }
        "ReduceMax" -> reduce(i[0]!!, a.intsOrNull("axes"), a.int("keepdims", 1) == 1, -Float.MAX_VALUE) { x, y -> if (y > x) y else x }
        "Conv" -> conv(i[0]!!, i[1]!!, i.getOrNull(2), a)
        else -> error("Operation $op is not supported")
    }

    private fun JsonObject.int(key: String, default: Int = 0) = (this[key] as? JsonPrimitive)?.int ?: default
    private fun JsonObject.intsOrNull(key: String) = (this[key] as? JsonArray)?.map { it.jsonPrimitive.int }?.toIntArray()
    private fun JsonObject.ints(key: String) = intsOrNull(key) ?: error("Attribute $key missing")

    private fun size(shape: IntArray) = shape.fold(1) { x, y -> x * y }
    private fun strides(shape: IntArray): IntArray {
        val s = IntArray(shape.size)
        var acc = 1
        for (k in shape.indices.reversed()) { s[k] = acc; acc *= shape[k] }
        return s
    }

    private inline fun unary(t: Tensor, f: (Float) -> Float) = Tensor(t.shape, FloatArray(t.data.size) { f(t.data[it]) })

    private fun broadcastShape(a: IntArray, b: IntArray): IntArray {
        val r = maxOf(a.size, b.size)
        return IntArray(r) { k ->
            val x = a.getOrElse(k - (r - a.size)) { 1 }
            val y = b.getOrElse(k - (r - b.size)) { 1 }
            require(x == y || x == 1 || y == 1) { "Cannot broadcast ${a.toList()} and ${b.toList()}" }
            maxOf(x, y)
        }
    }

    /** Offsets into [t] for every index of [out], with numpy broadcasting. */
    private fun offsets(t: Tensor, out: IntArray): IntArray {
        if (t.shape.contentEquals(out)) return IntArray(size(out)) { it }
        val r = out.size
        val ts = strides(t.shape)
        val eff = IntArray(r) { k ->
            val tk = k - (r - t.rank)
            if (tk < 0 || t.shape[tk] == 1) 0 else ts[tk]
        }
        val result = IntArray(size(out))
        val idx = IntArray(r)
        var off = 0
        for (n in result.indices) {
            result[n] = off
            var k = r - 1
            while (k >= 0) {
                idx[k]++
                off += eff[k]
                if (idx[k] < out[k]) break
                off -= eff[k] * idx[k]
                idx[k] = 0
                k--
            }
        }
        return result
    }

    private inline fun binary(a: Tensor, b: Tensor, f: (Float, Float) -> Float): Tensor {
        val shape = broadcastShape(a.shape, b.shape)
        if (b.data.size == 1) { val y = b.data[0]; return Tensor(shape, FloatArray(a.data.size) { f(a.data[it], y) }).also { require(a.shape.contentEquals(shape)) } }
        val oa = offsets(a, shape)
        val ob = offsets(b, shape)
        return Tensor(shape, FloatArray(oa.size) { f(a.data[oa[it]], b.data[ob[it]]) })
    }

    private fun where(c: Tensor, x: Tensor, y: Tensor): Tensor {
        val shape = broadcastShape(broadcastShape(c.shape, x.shape), y.shape)
        val oc = offsets(c, shape); val ox = offsets(x, shape); val oy = offsets(y, shape)
        return Tensor(shape, FloatArray(oc.size) { if (c.data[oc[it]] != 0f) x.data[ox[it]] else y.data[oy[it]] })
    }

    private fun reshape(t: Tensor, target: IntArray): Tensor {
        val shape = IntArray(target.size) { if (target[it] == 0) t.shape[it] else target[it] }
        val known = shape.filter { it != -1 }.fold(1) { x, y -> x * y }
        val fixed = IntArray(shape.size) { if (shape[it] == -1) t.data.size / known else shape[it] }
        return Tensor(fixed, t.data)
    }

    private fun unsqueeze(t: Tensor, axes: IntArray): Tensor {
        val r = t.rank + axes.size
        val norm = axes.map { if (it < 0) it + r else it }.toSet()
        val shape = IntArray(r)
        var j = 0
        for (k in 0 until r) shape[k] = if (k in norm) 1 else t.shape[j++]
        return Tensor(shape, t.data)
    }

    private fun transpose(t: Tensor, perm: IntArray): Tensor {
        val shape = IntArray(t.rank) { t.shape[perm[it]] }
        val src = strides(t.shape)
        val moved = IntArray(t.rank) { src[perm[it]] }
        val out = FloatArray(t.data.size)
        val idx = IntArray(t.rank)
        var off = 0
        for (n in out.indices) {
            out[n] = t.data[off]
            var k = t.rank - 1
            while (k >= 0) {
                idx[k]++; off += moved[k]
                if (idx[k] < shape[k]) break
                off -= moved[k] * idx[k]; idx[k] = 0; k--
            }
        }
        return Tensor(shape, out)
    }

    private fun concat(ts: List<Tensor>, axisIn: Int): Tensor {
        val r = ts[0].rank
        val axis = if (axisIn < 0) axisIn + r else axisIn
        val shape = ts[0].shape.copyOf().also { s -> s[axis] = ts.sumOf { it.shape[axis] } }
        val outer = size(shape.copyOfRange(0, axis))
        val out = FloatArray(size(shape))
        var pos = 0
        for (o in 0 until outer) for (t in ts) {
            val chunk = size(t.shape.copyOfRange(axis, r))
            t.data.copyInto(out, pos, o * chunk, o * chunk + chunk)
            pos += chunk
        }
        return Tensor(shape, out)
    }

    private fun slice(t: Tensor, starts: IntArray, ends: IntArray, axesIn: IntArray?, stepsIn: IntArray?): Tensor {
        val r = t.rank
        val start = IntArray(r); val step = IntArray(r) { 1 }; val count = t.shape.copyOf()
        val axes = axesIn ?: IntArray(starts.size) { it }
        for (k in axes.indices) {
            val ax = if (axes[k] < 0) axes[k] + r else axes[k]
            val dim = t.shape[ax]
            val st = stepsIn?.get(k) ?: 1
            fun clamp(v: Int, lo: Int, hi: Int) = if (v < lo) lo else if (v > hi) hi else v
            var s = starts[k].let { if (it < 0) it + dim else it }
            var e = ends[k].let { if (it < 0 && it > Int.MIN_VALUE / 2) it + dim else it }
            if (st > 0) { s = clamp(s, 0, dim); e = clamp(e, 0, dim) } else { s = clamp(s, 0, dim - 1); e = clamp(e, -1, dim - 1) }
            start[ax] = s; step[ax] = st
            count[ax] = if (st > 0) maxOf(0, (e - s + st - 1) / st) else maxOf(0, (s - e - st - 1) / -st)
        }
        val src = strides(t.shape)
        val out = FloatArray(size(count))
        val idx = IntArray(r)
        for (n in out.indices) {
            var off = 0
            for (k in 0 until r) off += (start[k] + idx[k] * step[k]) * src[k]
            out[n] = t.data[off]
            var k = r - 1
            while (k >= 0) { idx[k]++; if (idx[k] < count[k]) break; idx[k] = 0; k-- }
        }
        return Tensor(count, out)
    }

    private fun pad(t: Tensor, pads: IntArray, mode: String, value: Float): Tensor {
        val r = t.rank
        val shape = IntArray(r) { t.shape[it] + pads[it] + pads[it + r] }
        val src = strides(t.shape)
        val out = FloatArray(size(shape))
        val idx = IntArray(r)
        for (n in out.indices) {
            var off = 0
            var inside = true
            for (k in 0 until r) {
                var c = idx[k] - pads[k]
                val d = t.shape[k]
                if (c < 0 || c >= d) {
                    if (mode == "reflect") {
                        while (c < 0 || c >= d) c = if (c < 0) -c else 2 * (d - 1) - c
                    } else { inside = false; break }
                }
                off += c * src[k]
            }
            out[n] = if (inside) t.data[off] else value
            var k = r - 1
            while (k >= 0) { idx[k]++; if (idx[k] < shape[k]) break; idx[k] = 0; k-- }
        }
        return Tensor(shape, out)
    }

    private inline fun reduce(t: Tensor, axesIn: IntArray?, keep: Boolean, init: Float, f: (Float, Float) -> Float): Tensor {
        val r = t.rank
        val axes = (axesIn ?: IntArray(r) { it }).map { if (it < 0) it + r else it }.toSet()
        val kept = IntArray(r) { if (it in axes) 1 else t.shape[it] }
        val out = FloatArray(size(kept)) { init }
        val ks = strides(kept)
        val idx = IntArray(r)
        for (n in t.data.indices) {
            var o = 0
            for (k in 0 until r) if (k !in axes) o += idx[k] * ks[k]
            out[o] = f(out[o], t.data[n])
            var k = r - 1
            while (k >= 0) { idx[k]++; if (idx[k] < t.shape[k]) break; idx[k] = 0; k-- }
        }
        val shape = if (keep) kept else t.shape.filterIndexed { k, _ -> k !in axes }.toIntArray()
        return Tensor(shape, out)
    }

    /** 2-D convolution, NCHW, group 1. The inner loop runs over contiguous output columns. */
    private fun conv(x: Tensor, w: Tensor, bias: Tensor?, a: JsonObject): Tensor {
        val (n, c, h, wd) = x.shape.let { listOf(it[0], it[1], it[2], it[3]) }
        val (m, _, kh, kw) = w.shape.let { listOf(it[0], it[1], it[2], it[3]) }
        val st = a.intsOrNull("strides") ?: intArrayOf(1, 1)
        val dl = a.intsOrNull("dilations") ?: intArrayOf(1, 1)
        val pd = a.intsOrNull("pads") ?: intArrayOf(0, 0, 0, 0)
        val oh = (h + pd[0] + pd[2] - dl[0] * (kh - 1) - 1) / st[0] + 1
        val ow = (wd + pd[1] + pd[3] - dl[1] * (kw - 1) - 1) / st[1] + 1
        val out = FloatArray(n * m * oh * ow)
        for (b in 0 until n) for (oc in 0 until m) {
            val ob = (b * m + oc) * oh * ow
            val bv = bias?.data?.get(oc) ?: 0f
            for (k in 0 until oh * ow) out[ob + k] = bv
            for (ic in 0 until c) {
                val xb = (b * c + ic) * h * wd
                for (ky in 0 until kh) for (kx in 0 until kw) {
                    val wv = w.data[((oc * c + ic) * kh + ky) * kw + kx]
                    if (wv == 0f) continue
                    // Output columns whose input column is inside the image.
                    val offX = kx * dl[1] - pd[1]
                    var x0 = 0
                    while (x0 < ow && x0 * st[1] + offX < 0) x0++
                    var x1 = ow
                    while (x1 > x0 && (x1 - 1) * st[1] + offX >= wd) x1--
                    for (oy in 0 until oh) {
                        val iy = oy * st[0] + ky * dl[0] - pd[0]
                        if (iy < 0 || iy >= h) continue
                        val row = xb + iy * wd + offX
                        val o = ob + oy * ow
                        var ox = x0
                        while (ox < x1) { out[o + ox] += wv * x.data[row + ox * st[1]]; ox++ }
                    }
                }
            }
        }
        return Tensor(intArrayOf(n, m, oh, ow), out)
    }
}
