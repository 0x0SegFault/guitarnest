package app.guitarnest.tab

/** A small XML reader, enough for MusicXML. No DTD loading, no external entities (safe on untrusted files). */
class XmlNode(val name: String, val attributes: Map<String, String>, val children: List<XmlNode>, val text: String) {
    fun child(name: String): XmlNode? = children.firstOrNull { it.name == name }
    fun all(name: String): List<XmlNode> = children.filter { it.name == name }
    fun path(vararg names: String): XmlNode? = names.fold(this as XmlNode?) { node, n -> node?.child(n) }
    fun textOf(vararg names: String): String? = path(*names)?.text?.trim()?.takeIf { it.isNotEmpty() }
}

object Xml {
    fun parse(input: String): XmlNode {
        val stack = ArrayDeque<Triple<String, Map<String, String>, Pair<MutableList<XmlNode>, StringBuilder>>>()
        stack.addLast(Triple("#document", emptyMap(), mutableListOf<XmlNode>() to StringBuilder()))
        var i = 0
        while (i < input.length) {
            val lt = input.indexOf('<', i)
            if (lt < 0) break
            if (lt > i) stack.last().third.second.append(decode(input.substring(i, lt)))
            when {
                input.startsWith("<!--", lt) -> i = end(input, "-->", lt) + 3
                input.startsWith("<![CDATA[", lt) -> {
                    val e = end(input, "]]>", lt)
                    stack.last().third.second.append(input, lt + 9, e)
                    i = e + 3
                }
                input.startsWith("<?", lt) -> i = end(input, "?>", lt) + 2
                input.startsWith("<!", lt) -> i = skipDeclaration(input, lt)
                input.startsWith("</", lt) -> {
                    val gt = end(input, ">", lt)
                    val name = input.substring(lt + 2, gt).trim()
                    val (open, attrs, content) = stack.removeLast()
                    if (open != name) throw TabFormatException("Broken XML: </$name> closes <$open>")
                    stack.last().third.first += XmlNode(open, attrs, content.first, content.second.toString())
                    i = gt + 1
                }
                else -> {
                    val gt = tagEnd(input, lt)
                    val selfClosing = input[gt - 1] == '/'
                    val body = input.substring(lt + 1, if (selfClosing) gt - 1 else gt).trim()
                    val name = body.takeWhile { !it.isWhitespace() }
                    val attrs = ATTR.findAll(body.substring(name.length)).associate { it.groupValues[1] to decode(it.groupValues[3]) }
                    if (selfClosing) stack.last().third.first += XmlNode(name, attrs, emptyList(), "")
                    else stack.addLast(Triple(name, attrs, mutableListOf<XmlNode>() to StringBuilder()))
                    i = gt + 1
                }
            }
        }
        if (stack.size != 1) throw TabFormatException("Broken XML: <${stack.last().first}> is not closed")
        return stack.last().third.first.firstOrNull() ?: throw TabFormatException("Empty XML file")
    }

    private val ATTR = Regex("""([\w:.-]+)\s*=\s*(["'])(.*?)\2""", RegexOption.DOT_MATCHES_ALL)

    private fun end(s: String, token: String, from: Int): Int =
        s.indexOf(token, from).takeIf { it >= 0 } ?: throw TabFormatException("Broken XML: $token missing")

    private fun tagEnd(s: String, from: Int): Int {
        var quote: Char? = null
        for (k in from until s.length) {
            val c = s[k]
            if (quote != null) { if (c == quote) quote = null } else if (c == '"' || c == '\'') quote = c else if (c == '>') return k
        }
        throw TabFormatException("Broken XML: tag not closed")
    }

    /** Skips <!DOCTYPE ...> including an internal subset in [...]; entities in it are not used. */
    private fun skipDeclaration(s: String, from: Int): Int {
        var depth = 0
        for (k in from until s.length) {
            when (s[k]) { '[' -> depth++; ']' -> depth--; '>' -> if (depth == 0) return k + 1 }
        }
        throw TabFormatException("Broken XML: declaration not closed")
    }

    private fun decode(text: String): String {
        if ('&' !in text) return text
        return Regex("&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos);").replace(text) { m ->
            val e = m.groupValues[1]
            when {
                e == "amp" -> "&"; e == "lt" -> "<"; e == "gt" -> ">"; e == "quot" -> "\""; e == "apos" -> "'"
                e.startsWith("#x") -> codePoint(e.substring(2).toInt(16))
                else -> codePoint(e.substring(1).toInt())
            }
        }
    }

    private fun codePoint(cp: Int): String =
        if (cp < 0x10000) cp.toChar().toString()
        else { val v = cp - 0x10000; charArrayOf((0xD800 + (v shr 10)).toChar(), (0xDC00 + (v and 0x3FF)).toChar()).concatToString() }
}
