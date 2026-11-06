package jp.vcmc.rururules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookLayoutTest {
    private fun linesOf(page: String) = page.split('\n').sumOf { BookLayout.lineCount(it) }

    @Test
    fun `全角は 1 行に 12 文字と見積もる`() {
        assertEquals(1, BookLayout.lineCount("あ".repeat(12)))
        assertEquals(2, BookLayout.lineCount("あ".repeat(13)))
    }

    @Test
    fun `半角の幅は文字ごとに見積もる`() {
        assertEquals(1, BookLayout.lineCount("i".repeat(57)))
        assertEquals(2, BookLayout.lineCount("W".repeat(20)))
    }

    @Test
    fun `日本語の長文でもページの行数を超えない`() {
        val pages = BookLayout.paginate(listOf(RuleBlock("禁止事項です。".repeat(200))))
        assertTrue(pages.size > 1)
        pages.forEach { page ->
            assertTrue(linesOf(page) <= BookLayout.MAX_LINES, "行数が多すぎます: ${linesOf(page)}")
        }
    }

    @Test
    fun `分割しても文章は欠けない`() {
        val text = "あいうえお、かきくけこ。".repeat(100)
        val pages = BookLayout.paginate(listOf(RuleBlock(text)))
        assertEquals(text, pages.joinToString("") { it.replace("\n", "") })
    }

    @Test
    fun `半角の単語は途中で切らない`() {
        val words = List(120) { "word$it" }
        val pages = BookLayout.paginate(listOf(RuleBlock(words.joinToString(" "))))
        assertTrue(pages.size > 1)
        assertEquals(words, pages.flatMap { it.split(' ', '\n') }.filter { it.isNotEmpty() })
    }

    @Test
    fun `短い項目は同じページに並ぶ`() {
        val pages = BookLayout.paginate(listOf(RuleBlock("・項目1"), RuleBlock("・項目2")))
        assertEquals(listOf("・項目1\n・項目2"), pages)
    }

    @Test
    fun `見出しの前は 1 行空ける`() {
        val pages = BookLayout.paginate(listOf(RuleBlock("本文"), RuleBlock("■ 見出し", heading = true), RuleBlock("続き")))
        assertEquals(listOf("本文\n\n■ 見出し\n続き"), pages)
    }

    @Test
    fun `見出しだけがページの最後に残らない`() {
        val filler = List(BookLayout.MAX_LINES - 1) { RuleBlock("項目$it") }
        val pages = BookLayout.paginate(filler + RuleBlock("■ 見出し", heading = true) + RuleBlock("続き"))
        assertEquals(2, pages.size)
        assertEquals("■ 見出し\n続き", pages[1])
    }

    @Test
    fun `見出しの次の項目が入らなければ見出しごと次のページへ送る`() {
        val filler = List(BookLayout.MAX_LINES - 4) { RuleBlock("項目$it") }
        val longItem = RuleBlock("あ".repeat(12 * 3))
        val pages = BookLayout.paginate(filler + RuleBlock("■ 見出し", heading = true) + longItem)
        assertEquals(2, pages.size)
        assertEquals("■ 見出し\n${longItem.text}", pages[1])
    }

    @Test
    fun `1 ページに収まる項目は途中で切らない`() {
        val filler = List(BookLayout.MAX_LINES - 1) { RuleBlock("項目$it") }
        val item = RuleBlock("あ".repeat(12 * 2))
        val pages = BookLayout.paginate(filler + item)
        assertEquals(2, pages.size)
        assertEquals(item.text, pages[1])
    }

    @Test
    fun `空の入力ではページを作らない`() {
        assertTrue(BookLayout.paginate(emptyList()).isEmpty())
    }
}
