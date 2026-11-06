package jp.vcmc.rururules

import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RulesPageParserTest {
    private val selector = "main, article, .content, #content"

    /** vcmc.jp のルールページと同じ構造。記事の見出しが header の中にある。 */
    private val sitePage = """
        <html><body>
        <header class="site-header"><nav><ul><li><a>メニュー</a></li></ul></nav></header>
        <main id="main">
          <nav aria-label="パンくずリスト"><ol><li>ホーム</li></ol></nav>
          <p class="eyebrow">Server rules</p>
          <h1 class="page-title">ルール＆ガイドライン</h1>
          <article>
            <header class="rule-block-head">
              <div>
                <p class="eyebrow">Prohibited</p>
                <h2 class="rule-block-title">禁止事項</h2>
              </div>
            </header>
            <p class="rule-desc">違反が確認された場合は BAN の対象です。</p>
            <h3>配信関連</h3>
            <ul><li>配信URLを貼る行為</li><li>荒らし</li></ul>
          </article>
        </main>
        <footer><p>フッター</p></footer>
        </body></html>
    """.trimIndent()

    private fun parse(html: String, content: String = selector) =
        RulesPageParser.parse(Jsoup.parse(html), content, RulesPageParser.DEFAULT_EXCLUDE)

    @Test
    fun `記事の header にある見出しを残してページの飾りを除く`() {
        assertEquals(
            listOf(
                RuleBlock("【ルール＆ガイドライン】", heading = true),
                RuleBlock("【禁止事項】", heading = true),
                RuleBlock("違反が確認された場合は BAN の対象です。"),
                RuleBlock("■ 配信関連", heading = true),
                RuleBlock("・配信URLを貼る行為"),
                RuleBlock("・荒らし"),
            ),
            parse(sitePage),
        )
    }

    @Test
    fun `入れ子のリストを二重に載せない`() {
        val blocks = parse("<main><ul><li>親<ul><li>子</li></ul></li></ul></main>")
        assertEquals(listOf(RuleBlock("・親"), RuleBlock("・子")), blocks)
    }

    @Test
    fun `li の中の p を二重に載せない`() {
        val blocks = parse("<main><ul><li><p>本文</p></li></ul></main>")
        assertEquals(listOf(RuleBlock("本文")), blocks)
    }

    @Test
    fun `本文の要素が無いときは文章を句点で分ける`() {
        val blocks = parse("<main><div>ひとつめ。ふたつめ！</div></main>")
        assertEquals(listOf(RuleBlock("ひとつめ"), RuleBlock("ふたつめ")), blocks)
    }

    @Test
    fun `セレクタが一致しないときは body 全体を使う`() {
        assertFalse(parse("<body><p>本文</p></body>", "#none").isEmpty())
    }

    @Test
    fun `本文が無いページは空になる`() {
        assertTrue(parse("<body></body>").isEmpty())
    }
}
