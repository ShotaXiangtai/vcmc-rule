package jp.vcmc.rururules

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** ルールページの HTML から本に載せる文章を取り出す。 */
object RulesPageParser {
    /**
     * 本文ではない要素。
     * `<header>` は記事の見出しにも使われるので、ページ全体のヘッダーだけを除く。
     */
    const val DEFAULT_EXCLUDE =
        "nav, footer, script, style, noscript, form, button, .nav, .header, .footer, .sidebar, .site-header, .eyebrow, body > header"

    private const val BLOCKS = "h1, h2, h3, h4, h5, p, li, dt, dd"

    fun parse(document: Document, contentSelector: String, excludeSelector: String): List<RuleBlock> {
        if (excludeSelector.isNotBlank()) document.select(excludeSelector).remove()
        val root = contentSelector.takeIf { it.isNotBlank() }?.let { document.selectFirst(it) } ?: document.body()
        val blocks = root.select(BLOCKS).mapNotNull(::toBlock)
        return blocks.ifEmpty { splitPlainText(root.text()) }
    }

    private fun toBlock(element: Element): RuleBlock? {
        // 入れ子のブロックは内側の要素が自分で出力するので、外側は直下の文字だけを使う。二重に載せないため。
        val hasNestedBlock = element.select(BLOCKS).size > 1
        val text = (if (hasNestedBlock) element.ownText() else element.text()).trim()
        if (text.isEmpty()) return null
        return when (element.normalName()) {
            "h1", "h2" -> RuleBlock("【$text】", heading = true)
            "h3", "h4", "h5" -> RuleBlock("■ $text", heading = true)
            "li" -> RuleBlock("・$text")
            else -> RuleBlock(text)
        }
    }

    private fun splitPlainText(text: String): List<RuleBlock> =
        text.split(Regex("[。！？\n]+")).map { it.trim() }.filter { it.isNotEmpty() }.map { RuleBlock(it) }
}
