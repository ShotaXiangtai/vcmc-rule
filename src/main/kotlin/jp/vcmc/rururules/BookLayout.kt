package jp.vcmc.rururules

/** 本に載せる文章のまとまり。見出しは前を 1 行空ける。 */
data class RuleBlock(val text: String, val heading: Boolean = false)

/**
 * 文章を本のページに分ける。
 * 本の 1 ページは文字数ではなく表示幅で決まり、全角文字は 1 行に 12 文字ほどしか入らない。
 * 文字数で区切ると日本語がページからはみ出して読めなくなるので、幅を見積もって行数で判断する。
 * 実際の折り返しはクライアントが行う。空白があれば空白で、無ければ文字の途中で折り返すので、ここでも同じように数える。
 */
object BookLayout {
    /** 本の 1 行の幅（ピクセル）。 */
    private const val LINE_WIDTH = 114

    /** 本は 14 行表示できる。見積もりが外れてもはみ出さないよう 1 行余らせる。 */
    const val MAX_LINES = 13

    private const val FULL_WIDTH = 9
    private const val DEFAULT_WIDTH = 6

    /** デフォルトフォントの半角文字の幅（文字の後ろの 1 ピクセルを含む）。 */
    private val NARROW_WIDTHS = buildMap {
        "!,.:;i|'".forEach { put(it, 2) }
        "l`".forEach { put(it, 3) }
        " It[]".forEach { put(it, 4) }
        "\"()*<>fk{}".forEach { put(it, 5) }
        "@~".forEach { put(it, 7) }
    }

    fun charWidth(char: Char): Int =
        if (char.code < 0x80) NARROW_WIDTHS[char] ?: DEFAULT_WIDTH else FULL_WIDTH

    /** 表示に必要な行数の見積もり。 */
    fun lineCount(text: String): Int = lineEnds(text).size

    /** 折り返したときの各行の終わりの位置（次の行の先頭の文字の位置）。 */
    private fun lineEnds(text: String): List<Int> {
        val ends = mutableListOf<Int>()
        var width = 0
        var lastSpace = -1
        var index = 0
        while (index < text.length) {
            val char = text[index]
            val charWidth = charWidth(char)
            if (width + charWidth > LINE_WIDTH) {
                val end = if (lastSpace >= 0) lastSpace + 1 else index
                ends += end
                index = end
                width = 0
                lastSpace = -1
                continue
            }
            if (char == ' ') lastSpace = index
            width += charWidth
            index++
        }
        ends += text.length
        return ends
    }

    /** 各ページの文字列を返す。長い文章はページをまたいで分割する。 */
    fun paginate(blocks: List<RuleBlock>): List<String> {
        val pages = mutableListOf<String>()
        val page = StringBuilder()
        var usedLines = 0

        fun flush() {
            if (page.isNotEmpty()) pages += page.toString().trimEnd()
            page.clear()
            usedLines = 0
        }

        for ((index, block) in blocks.withIndex()) {
            val needed = lineCount(block.text)
            if (block.heading && usedLines > 0) {
                // 見出しだけがページの最後に残らないよう、次の項目と一緒に入らなければ次のページへ送る。
                val following = blocks.getOrNull(index + 1)?.let { lineCount(it.text) } ?: 0
                if (usedLines + 1 + needed + following > MAX_LINES) {
                    flush()
                } else {
                    page.append('\n')
                    usedLines++
                }
            } else if (usedLines > 0 && usedLines + needed > MAX_LINES && needed <= MAX_LINES) {
                // 1 ページに収まる項目は途中で切らない。
                flush()
            }
            var rest = block.text
            while (rest.isNotEmpty()) {
                if (usedLines >= MAX_LINES) flush()
                val (head, tail) = splitAtLines(rest, MAX_LINES - usedLines)
                page.append(head).append('\n')
                usedLines += lineCount(head)
                rest = tail
            }
        }
        flush()
        return pages
    }

    /** 先頭から maxLines 行に収まる部分と残りに分ける。半角の単語は途中で切らない。 */
    private fun splitAtLines(text: String, maxLines: Int): Pair<String, String> {
        val ends = lineEnds(text)
        if (ends.size <= maxLines) return text to ""
        val cut = ends[maxLines - 1]
        return text.substring(0, cut).trimEnd() to text.substring(cut).trimStart()
    }
}
