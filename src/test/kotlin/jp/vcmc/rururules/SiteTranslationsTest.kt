package jp.vcmc.rururules

import jp.vcmc.rururules.i18n.Language
import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SiteTranslationsTest {
    /** サイトの i18n.js と同じ形。コメント、エスケープ、香港の列の省略を含む。 */
    private val script = """
        /* 先頭のコメント */
        window.VCMC_I18N_ROWS = [
          // ── 共通 ──
          ['ルール', 'Rules', '规则', '規則'],
          ['禁止事項', 'Prohibited actions', '禁止事项', '禁止事項', '禁止事項（香港）'],
          ['合意のない  PvP行為', 'PvP without mutual consent', '未经双方同意的 PvP', '未經雙方同意的 PvP'],
          ['参加前に<a href="rules.html">ルール</a>を確認', 'Read the <a href="rules.html">rules</a> first', '先阅读<a href="rules.html">规则</a>', '請先閱讀<a href="rules.html">規則</a>'],
          ['It\'s 日本語', 'It\'s English', "简体", "台灣"],
          ['空の訳', '', null, null],
        ];
        (function () { var x = '[ここは読まない]'; })();
    """.trimIndent()

    private val translations = SiteTranslations.parse(script)

    private fun translated(html: String, language: Language): String {
        val document = Jsoup.parse(html)
        translations.translate(document, language)
        // 置き換えた要素には、サイトと同じく data-i18n-done が付く。比べるときは除く。
        return document.body().html().replace(" data-i18n-done=\"\"", "")
    }

    @Test
    fun `表の行を読み、空白をまとめて比べる`() {
        assertEquals("<p>PvP without mutual consent</p>", translated("<p>合意のない\n  PvP行為</p>", Language.ENGLISH))
        assertEquals("<p>It's English</p>", translated("<p>It's 日本語</p>", Language.ENGLISH))
    }

    @Test
    fun `香港の列が無い行は台灣の文を使う`() {
        assertEquals("<h2>規則</h2><h2>禁止事項（香港）</h2>", translated("<h2>ルール</h2><h2>禁止事項</h2>", Language.TRADITIONAL_CHINESE_HONG_KONG))
        assertEquals("<h2>規則</h2><h2>禁止事項</h2>", translated("<h2>ルール</h2><h2>禁止事項</h2>", Language.TRADITIONAL_CHINESE_TAIWAN))
    }

    @Test
    fun `タグを含む文は要素の中身ごと置き換える`() {
        assertEquals(
            "<p>Read the <a href=\"rules.html\">rules</a> first</p>",
            translated("<p>参加前に<a href=\"rules.html\">ルール</a>を確認</p>", Language.ENGLISH),
        )
    }

    @Test
    fun `アイコンと並んだ文字はテキストの部分だけ置き換える`() {
        assertEquals("<li><i class=\"icon\"></i> Rules </li>", translated("<li><i class=\"icon\"></i> ルール </li>", Language.ENGLISH))
    }

    @Test
    fun `表に無い文とスクリプトの中は日本語のまま`() {
        assertEquals("<p>表に無い文</p><script>var s = 'ルール';</script>", translated("<p>表に無い文</p><script>var s = 'ルール';</script>", Language.ENGLISH))
    }

    @Test
    fun `空の訳も表のとおりに使い、訳の無い言語は日本語のまま`() {
        assertEquals("<p></p>", translated("<p>空の訳</p>", Language.ENGLISH))
        assertEquals("<p>空の訳</p>", translated("<p>空の訳</p>", Language.SIMPLIFIED_CHINESE))
    }

    @Test
    fun `日本語は翻訳しない`() {
        assertFalse(translations.supports(Language.JAPANESE))
        assertTrue(translations.supports(Language.ENGLISH))
        assertEquals("<p>ルール</p>", translated("<p>ルール</p>", Language.JAPANESE))
    }

    @Test
    fun `翻訳したページからルールの文章を取り出せる`() {
        val document = Jsoup.parse("<main><h2>禁止事項</h2><ul><li>合意のない PvP行為</li><li>表に無い文</li></ul></main>")
        translations.translate(document, Language.ENGLISH)
        val blocks = RulesPageParser.parse(document, "main", RulesPageParser.DEFAULT_EXCLUDE)
        assertEquals(listOf("【Prohibited actions】", "・PvP without mutual consent", "・表に無い文"), blocks.map { it.text })
    }

    @Test
    fun `表が無いか読めないときは失敗する`() {
        assertFailsWith<IllegalArgumentException> { SiteTranslations.parse("var other = [];") }
        assertFailsWith<IllegalArgumentException> { SiteTranslations.parse("window.VCMC_I18N_ROWS = [['ルール', 'Rules'") }
        assertFailsWith<IllegalArgumentException> { SiteTranslations.parse("window.VCMC_I18N_ROWS = [];") }
    }

    @Test
    fun `チャットのリンクには言語を付ける`() {
        assertEquals("https://www.vcmc.jp/rules?lang=en", SiteTranslations.linkFor("https://www.vcmc.jp/rules", Language.ENGLISH))
        assertEquals("https://www.vcmc.jp/rules?lang=zh-HK", SiteTranslations.linkFor("https://www.vcmc.jp/rules", Language.TRADITIONAL_CHINESE_HONG_KONG))
        assertEquals("https://www.vcmc.jp/rules?a=1&lang=ja#top", SiteTranslations.linkFor("https://www.vcmc.jp/rules?a=1#top", Language.JAPANESE))
        assertEquals("https://www.vcmc.jp/rules?lang=en", SiteTranslations.linkFor("https://www.vcmc.jp/rules?lang=en", Language.SIMPLIFIED_CHINESE))
    }
}
