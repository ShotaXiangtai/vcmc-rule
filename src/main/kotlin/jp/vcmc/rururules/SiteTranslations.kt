package jp.vcmc.rururules

import jp.vcmc.rururules.i18n.Language
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

/**
 * サイトの i18n.js にある対訳表（window.VCMC_I18N_ROWS）で、日本語のルールページを翻訳する。
 * サイトはページを日本語で配信し、ブラウザの JavaScript で表の文と一致した部分を置き換えている。
 * 本は JavaScript を実行せずに HTML を読むので、同じ規則での置き換えをここで行う。
 *
 * 表の列は [日本語, English, 简体中文, 繁體中文（台灣）, 繁體中文（香港）]。香港の列が無い行は台灣の文を使う。
 */
class SiteTranslations private constructor(private val tables: Map<Language, Map<String, String>>) {

    /** その言語の文が 1 つでもあるか。 */
    fun supports(language: Language): Boolean = tables[language]?.isNotEmpty() == true

    /** document を翻訳する。document 自体を書き換える。 */
    fun translate(document: Document, language: Language) {
        val table = tables[language] ?: return
        document.outputSettings().prettyPrint(false)
        val body = document.body()
        val lookup = { text: String -> normalize(text).takeIf { it.isNotEmpty() }?.let(table::get) }

        // 1) 要素の中身がまるごと表の文と一致するもの（<b> や <a> を含む文）
        for (element in body.select("*")) {
            if (element === body || element.normalName() in SKIP || element.ownerDocument() == null) continue
            val html = element.html()
            if (html.isEmpty() || html.length > MAX_HTML_LENGTH) continue
            val translated = lookup(html) ?: continue
            element.html(translated)
            element.attr(DONE, "")
        }

        // 2) アイコンと並んでいる文字など、テキストの部分だけが一致するもの
        val textNodes = mutableListOf<TextNode>()
        NodeTraversor.traverse(NodeVisitor { node, _ -> if (node is TextNode) textNodes += node }, body)
        for (node in textNodes) {
            val parent = node.parent() as? Element ?: continue
            if (parent.normalName() in SKIP || parent.closest("[$DONE]") != null) continue
            val raw = node.wholeText
            val translated = lookup(raw) ?: continue
            val leading = raw.takeWhile(Char::isWhitespace)
            val trailing = raw.takeLastWhile(Char::isWhitespace)
            node.text(leading + translated + trailing)
        }
    }

    companion object {
        private const val VARIABLE = "VCMC_I18N_ROWS"
        private const val DONE = "data-i18n-done"
        private const val MAX_HTML_LENGTH = 1500
        private val SKIP = setOf("script", "style", "textarea", "select", "option")
        private val WHITESPACE = Regex("""\s+""")

        /** 表の列の番号。 */
        private val COLUMNS = mapOf(
            Language.ENGLISH to 1,
            Language.SIMPLIFIED_CHINESE to 2,
            Language.TRADITIONAL_CHINESE_TAIWAN to 3,
            Language.TRADITIONAL_CHINESE_HONG_KONG to 4,
        )
        private const val HONG_KONG = 4
        private const val TAIWAN = 3

        /** サイトと同じく、空白をまとめて前後を削ってから比べる。 */
        fun normalize(text: String): String = text.replace(WHITESPACE, " ").trim()

        /** サイトの ?lang= に渡す値。 */
        private val SITE_CODES = mapOf(
            Language.JAPANESE to "ja",
            Language.ENGLISH to "en",
            Language.SIMPLIFIED_CHINESE to "zh-CN",
            Language.TRADITIONAL_CHINESE_TAIWAN to "zh-TW",
            Language.TRADITIONAL_CHINESE_HONG_KONG to "zh-HK",
        )
        private val LANG_PARAMETER = Regex("""[?&]lang=""")

        /** サイトのページを、その言語で表示させる URL。すでに lang の指定があれば変えない。 */
        fun linkFor(url: String, language: Language): String {
            val hash = url.indexOf('#')
            val base = if (hash >= 0) url.substring(0, hash) else url
            val fragment = if (hash >= 0) url.substring(hash) else ""
            if (LANG_PARAMETER.containsMatchIn(base)) return url
            val separator = if ('?' in base) '&' else '?'
            return "$base${separator}lang=${SITE_CODES.getValue(language)}$fragment"
        }

        /** i18n.js の中身から対訳表を読む。読めないときは IllegalArgumentException。 */
        fun parse(script: String): SiteTranslations {
            val rows = ScriptReader(script).readRows(VARIABLE)
            val tables = COLUMNS.mapValues { (_, column) ->
                val table = LinkedHashMap<String, String>()
                for (row in rows) {
                    val source = row.getOrNull(0)
                    val text = row.getOrNull(column) ?: if (column == HONG_KONG) row.getOrNull(TAIWAN) else null
                    if (!source.isNullOrEmpty() && text != null) table[normalize(source)] = text
                }
                table
            }
            require(tables.values.any { it.isNotEmpty() }) { "$VARIABLE に翻訳がありません" }
            return SiteTranslations(tables)
        }
    }
}

/** JavaScript の配列のリテラルのうち、文字列と null と配列だけを読む。 */
private class ScriptReader(private val text: String) {
    private var pos = 0

    fun readRows(variable: String): List<List<String?>> {
        // 後ろの処理でも同じ名前を使っているので、代入しているところを探す。
        val assignment = Regex("""\b${Regex.escape(variable)}\s*=(?!=)""").find(text)
        requireNotNull(assignment) { "$variable が見つかりません" }
        pos = assignment.range.last + 1
        skipSpace()
        val value = readValue()
        require(value is List<*>) { "$variable が配列ではありません" }
        return value.map { row ->
            require(row is List<*>) { "$variable の行が配列ではありません" }
            row.map { cell ->
                require(cell == null || cell is String) { "$variable の値が文字列ではありません" }
                cell as String?
            }
        }
    }

    private fun readValue(): Any? {
        skipSpace()
        require(pos < text.length) { "途中で終わっています" }
        return when (val c = text[pos]) {
            '[' -> readArray()
            '\'', '"', '`' -> readString(c)
            else -> {
                require(text.startsWith("null", pos)) { "読めない値があります（${pos} 文字目）" }
                pos += 4
                null
            }
        }
    }

    private fun readArray(): List<Any?> {
        expect('[')
        val items = mutableListOf<Any?>()
        while (true) {
            skipSpace()
            if (peek() == ']') {
                pos++
                return items
            }
            items += readValue()
            skipSpace()
            when (peek()) {
                ',' -> pos++
                ']' -> {
                    pos++
                    return items
                }
                else -> throw IllegalArgumentException("配列の区切りがありません（${pos} 文字目）")
            }
        }
    }

    private fun readString(quote: Char): String {
        expect(quote)
        val out = StringBuilder()
        while (true) {
            require(pos < text.length) { "文字列が閉じていません" }
            val c = text[pos++]
            when {
                c == quote -> return out.toString()
                c == '\\' -> out.append(readEscape())
                quote == '`' && c == '$' && peek() == '{' -> throw IllegalArgumentException("テンプレート文字列の式は読めません（${pos} 文字目）")
                else -> out.append(c)
            }
        }
    }

    private fun readEscape(): String {
        require(pos < text.length) { "文字列が閉じていません" }
        return when (val c = text[pos++]) {
            'n' -> "\n"
            't' -> "\t"
            'r' -> "\r"
            'b' -> "\b"
            'f' -> "\u000C"
            'v' -> "\u000B"
            '0' -> "\u0000"
            'x' -> hex(2)
            'u' -> if (peek() == '{') {
                val end = text.indexOf('}', pos)
                require(end > pos) { "\\u{...} が閉じていません" }
                val code = text.substring(pos + 1, end).toInt(16)
                pos = end + 1
                String(Character.toChars(code))
            } else {
                hex(4)
            }
            // 行の継続
            '\n' -> ""
            '\r' -> {
                if (peek() == '\n') pos++
                ""
            }
            else -> c.toString()
        }
    }

    private fun hex(length: Int): String {
        require(pos + length <= text.length) { "エスケープが途中で終わっています" }
        val code = text.substring(pos, pos + length).toInt(16)
        pos += length
        return code.toChar().toString()
    }

    private fun skipSpace() {
        while (pos < text.length) {
            when {
                text[pos].isWhitespace() -> pos++
                text.startsWith("//", pos) -> {
                    val end = text.indexOf('\n', pos)
                    pos = if (end < 0) text.length else end + 1
                }
                text.startsWith("/*", pos) -> {
                    val end = text.indexOf("*/", pos + 2)
                    require(end >= 0) { "コメントが閉じていません" }
                    pos = end + 2
                }
                else -> return
            }
        }
    }

    private fun peek(): Char? = text.getOrNull(pos)

    private fun expect(c: Char) {
        require(peek() == c) { "'$c' がありません（${pos} 文字目）" }
        pos++
    }
}
