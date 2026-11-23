package app.guitarnest.report

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Anonymous bug reports and ideas, sent to GuitarNest's report service (reports/, a Scaleway function in Paris)
 * which files them as issues in a private repository. The service never reads request headers, so it does
 * not see or store the IP address. Anti-spam is a small proof-of-work puzzle, not a tracking CAPTCHA.
 */
object Reports {
    /** The deployed service, for example "https://guitarnestreports-xxxx.functions.fnc.fr-par.scw.cloud". Null: not deployed yet. */
    val SERVICE_URL: String? = null

    const val GITHUB_NEW_ISSUE = "https://github.com/0x0SegFault/guitarnest/issues/new"

    @Serializable
    data class Challenge(val algorithm: String, val challenge: String, val maxnumber: Int, val salt: String, val signature: String)

    @Serializable
    data class Proof(val algorithm: String, val challenge: String, val number: Int, val salt: String, val signature: String)

    @Serializable
    data class Report(val kind: String, val title: String, val details: String, val version: String, val device: String, val proof: Proof? = null)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun parseChallenge(body: String): Challenge = json.decodeFromString(Challenge.serializer(), body)

    /** Finds the number whose SHA-256 with the salt is the challenge (at most [Challenge.maxnumber] tries, under a second). */
    fun solve(c: Challenge): Proof? {
        for (n in 0..c.maxnumber) {
            if (Sha256.hex((c.salt + n).encodeToByteArray()) == c.challenge) return Proof(c.algorithm, c.challenge, n, c.salt, c.signature)
        }
        return null
    }

    /** The JSON body for POST /report. [details] may include a crash log; it is cut to the service limit. */
    fun body(kind: String, title: String, details: String, version: String, device: String, proof: Proof): String =
        json.encodeToString(Report.serializer(), Report(kind, title.take(120), details.take(5000), version.take(40), device.take(120), proof))

    /** A GitHub "new issue" link with the text filled in (the player needs a GitHub account; the issue is public). */
    fun githubUrl(kind: String, title: String, details: String, version: String, device: String): String {
        val body = "$details\n\n---\nApp version: $version\nDevice: $device"
        val label = if (kind == "bug") "bug" else "enhancement"
        return "$GITHUB_NEW_ISSUE?labels=$label&title=${encode(title)}&body=${encode(body.take(6000))}"
    }

    /** Plain text to share with any app (e-mail, messenger). */
    fun shareText(kind: String, title: String, details: String, version: String, device: String): String =
        "GuitarNest ${if (kind == "bug") "problem" else "idea"}: $title\n\n$details\n\nApp version: $version\nDevice: $device"

    private fun encode(text: String): String = buildString {
        for (b in text.encodeToByteArray()) {
            val c = b.toInt() and 0xFF
            if (c < 128 && (c.toChar().isLetterOrDigit() || c.toChar() in "-_.~")) append(c.toChar())
            else append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 15])
        }
    }
}

/** SHA-256 (FIPS 180-4) in plain Kotlin, so Android and iOS share the anti-spam solver. */
object Sha256 {
    private val K = intArrayOf(
        0x428a2f98, 0x71374491, -0x4a3f0431, -0x164a245b, 0x3956c25b, 0x59f111f1, -0x6dc07d5c, -0x54e3a12b,
        -0x27f85568, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, -0x7f214e02, -0x6423f959, -0x3e640e8c,
        -0x1b64963f, -0x1041b87a, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        -0x67c1aeae, -0x57ce3993, -0x4ffcd838, -0x40a68039, -0x391ff40d, -0x2a586eb9, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, -0x7e3d36d2, -0x6d8dd37b,
        -0x5d40175f, -0x57e599b5, -0x3db47490, -0x3893ae5d, -0x2e6d17e7, -0x2966f9dc, -0xbf1ca7b, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, -0x7b3787ec, -0x7338fdf8, -0x6f410006, -0x5baf9315, -0x41065c09, -0x398e870e,
    )

    fun hex(message: ByteArray): String = digest(message).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    fun digest(message: ByteArray): ByteArray {
        val h = intArrayOf(0x6a09e667, -0x4498517b, 0x3c6ef372, -0x5ab00ac6, 0x510e527f, -0x64fa9774, 0x1f83d9ab, 0x5be0cd19)
        val bitLength = message.size.toLong() * 8
        val padded = ByteArray(((message.size + 9 + 63) / 64) * 64)
        message.copyInto(padded)
        padded[message.size] = 0x80.toByte()
        for (i in 0 until 8) padded[padded.size - 1 - i] = (bitLength ushr (8 * i)).toByte()
        val w = IntArray(64)
        for (block in padded.indices step 64) {
            for (t in 0 until 16) {
                val o = block + t * 4
                w[t] = ((padded[o].toInt() and 0xFF) shl 24) or ((padded[o + 1].toInt() and 0xFF) shl 16) or
                    ((padded[o + 2].toInt() and 0xFF) shl 8) or (padded[o + 3].toInt() and 0xFF)
            }
            for (t in 16 until 64) {
                val s0 = w[t - 15].rotateRight(7) xor w[t - 15].rotateRight(18) xor (w[t - 15] ushr 3)
                val s1 = w[t - 2].rotateRight(17) xor w[t - 2].rotateRight(19) xor (w[t - 2] ushr 10)
                w[t] = w[t - 16] + s0 + w[t - 7] + s1
            }
            var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]; var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
            for (t in 0 until 64) {
                val t1 = hh + (e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)) + ((e and f) xor (e.inv() and g)) + K[t] + w[t]
                val t2 = (a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)) + ((a and b) xor (a and c) xor (b and c))
                hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
            }
            h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += hh
        }
        return ByteArray(32) { i -> (h[i / 4] ushr (24 - 8 * (i % 4))).toByte() }
    }
}
