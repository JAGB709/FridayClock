package com.jarvis.clock.ui

/**
 * Markdown mínimo y seguro para la vista de contenido del agente. Solo produce texto con estilo
 * (títulos, párrafos, viñetas, citas, negrita, cursiva y código): nunca HTML, imágenes ni enlaces
 * activos (de `[texto](url)` se queda `texto`). Con límites para no pintar un muro de texto.
 */
object SafeMarkdown {
    const val MAX_CHARS = 4000
    const val MAX_BLOCKS = 40

    enum class Kind { HEADING, PARAGRAPH, BULLET, QUOTE }

    data class Run(val text: String, val bold: Boolean = false, val italic: Boolean = false, val code: Boolean = false)

    /** [level]: 1 a 3 en los títulos; en las viñetas, [marker] es "•" o "1." */
    data class Block(val kind: Kind, val runs: List<Run>, val level: Int = 0, val marker: String = "") {
        val plain: String get() = runs.joinToString("") { it.text }
    }

    private val HEADING = Regex("^(#{1,3})\\s+(.+)$")
    private val BULLET = Regex("^[-*•]\\s+(.+)$")
    private val NUMBERED = Regex("^(\\d{1,2})[.)]\\s+(.+)$")
    private val LINK = Regex("\\[([^\\]]*)]\\((?:[^()]|\\([^()]*\\))*\\)")
    private val IMAGE = Regex("!\\[([^\\]]*)]\\((?:[^()]|\\([^()]*\\))*\\)")
    private val TAGS = Regex("<[^>]{0,200}>")

    fun parse(src: String): List<Block> {
        val text = src.take(MAX_CHARS).replace("\r\n", "\n").replace('\r', '\n')
        val blocks = ArrayList<Block>()
        val para = StringBuilder()
        fun flush() {
            if (para.isNotBlank()) blocks.add(Block(Kind.PARAGRAPH, inline(para.toString().trim())))
            para.setLength(0)
        }
        for (raw in text.split("\n")) {
            if (blocks.size >= MAX_BLOCKS) break
            val line = raw.trim()
            val h = HEADING.matchEntire(line)
            val b = BULLET.matchEntire(line)
            val n = NUMBERED.matchEntire(line)
            when {
                line.isEmpty() -> flush()
                h != null -> { flush(); blocks.add(Block(Kind.HEADING, inline(h.groupValues[2]), level = h.groupValues[1].length)) }
                b != null -> { flush(); blocks.add(Block(Kind.BULLET, inline(b.groupValues[1]), marker = "•")) }
                n != null -> { flush(); blocks.add(Block(Kind.BULLET, inline(n.groupValues[2]), marker = n.groupValues[1] + ".")) }
                line.startsWith(">") -> { flush(); blocks.add(Block(Kind.QUOTE, inline(line.removePrefix(">").trim()))) }
                else -> { if (para.isNotEmpty()) para.append(' '); para.append(line) }
            }
        }
        flush()
        return blocks.take(MAX_BLOCKS)
    }

    /** Negrita (**), cursiva (* o _) y código (`). Lo que no cierra se deja como texto. */
    fun inline(s0: String): List<Run> {
        val s = TAGS.replace(LINK.replace(IMAGE.replace(s0) { it.groupValues[1] }) { it.groupValues[1] }, "")
        val out = ArrayList<Run>()
        val buf = StringBuilder()
        var bold = false
        var italic = false
        fun emit(code: Boolean = false) {
            if (buf.isNotEmpty()) out.add(Run(buf.toString(), bold, italic, code))
            buf.setLength(0)
        }
        var i = 0
        while (i < s.length) {
            when {
                s.startsWith("**", i) && (bold || s.indexOf("**", i + 2) > 0) -> { emit(); bold = !bold; i += 2 }
                s[i] == '`' && s.indexOf('`', i + 1) > 0 -> {
                    emit()
                    val end = s.indexOf('`', i + 1)
                    buf.append(s, i + 1, end)
                    emit(code = true)
                    i = end + 1
                }
                (s[i] == '*' || s[i] == '_') && (italic || (s.indexOf(s[i], i + 2) > 0 && i + 1 < s.length && s[i + 1] != ' ' && s[i + 1] != s[i])) -> {
                    emit(); italic = !italic; i += 1
                }
                else -> { buf.append(s[i]); i += 1 }
            }
        }
        emit()
        return out
    }
}
