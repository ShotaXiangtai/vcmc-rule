package jp.vcmc.rururules

import net.kyori.adventure.inventory.Book
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.entity.Player
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.concurrent.atomic.AtomicBoolean

class RulesBookFetcher(private val plugin: RuruRulesPlugin) {

    companion object {
        private const val MAX_CHARS_PER_PAGE = 220
        private const val MAX_BOOK_PAGES = 100
    }

    @Volatile
    private var cachedPages: List<Component>? = null

    @Volatile
    var isFetchFailed: Boolean = false
        private set

    private val fetching = AtomicBoolean(false)

    fun fetchAsync() {
        if (!fetching.compareAndSet(false, true)) return
        isFetchFailed = false

        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            try {
                val url = plugin.config.getString("rules-url", "https://www.vcmc.jp/rules") ?: "https://www.vcmc.jp/rules"
                val timeout = plugin.config.getInt("fetch-timeout-ms", 10000)
                val selector = plugin.config.getString("content-selector", "main, article, .content, #content") ?: ""

                plugin.logger.info("ルールページ取得開始: $url")

                val doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; RuruRules/1.0)")
                    .timeout(timeout)
                    .get()

                // 不要要素を除去
                doc.select("nav, header, footer, script, style, .nav, .header, .footer, .sidebar").remove()

                var content: Element? = null
                if (selector.isNotBlank()) {
                    content = doc.selectFirst(selector)
                }
                if (content == null) {
                    content = doc.body()
                }

                val paragraphs = extractParagraphs(content)

                if (paragraphs.isEmpty()) {
                    plugin.logger.warning("ルールページのテキスト抽出が空でした。チャット表示にフォールバックします。")
                    isFetchFailed = true
                    return@runNow
                }

                val pages = buildBookPages(paragraphs)
                cachedPages = pages
                plugin.logger.info("ルールページ取得完了 (${pages.size} ページ)")
            } catch (e: Exception) {
                plugin.logger.warning("ルールページ取得失敗: ${e.message}")
                isFetchFailed = true
            } finally {
                fetching.set(false)
            }
        }
    }

    private fun extractParagraphs(root: Element): List<String> {
        val result = mutableListOf<String>()
        val elements = root.select("h1, h2, h3, h4, h5, p, li, dt, dd")

        for (el in elements) {
            var text = el.text().trim()
            if (text.isEmpty()) continue

            when (el.tagName().lowercase()) {
                "h1", "h2" -> text = "【$text】"
                "h3", "h4", "h5" -> text = "■ $text"
                "li" -> text = "・$text"
            }
            result.add(text)
        }

        if (result.isEmpty()) {
            val wholeText = root.text()
            for (line in wholeText.split(Regex("[。！？\n]{1,3}"))) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) result.add(trimmed)
            }
        }

        return result
    }

    private fun buildBookPages(paragraphs: List<String>): List<Component> {
        val pages = mutableListOf<Component>()
        var buf = StringBuilder()

        for (para in paragraphs) {
            val chunks = splitToChunks(para)
            for (chunk in chunks) {
                if (buf.length + chunk.length + 1 > MAX_CHARS_PER_PAGE) {
                    if (buf.isNotEmpty()) {
                        pages.add(Component.text(buf.toString().trimEnd()))
                        buf = StringBuilder()
                    }
                }
                buf.append(chunk).append("\n")
            }
            if (buf.length + 1 <= MAX_CHARS_PER_PAGE) {
                buf.append("\n")
            }
        }

        if (buf.isNotEmpty()) {
            pages.add(Component.text(buf.toString().trimEnd()))
        }

        while (pages.size >= MAX_BOOK_PAGES) {
            pages.removeAt(pages.size - 1)
        }
        pages.add(buildAgreePage())

        return pages
    }

    private fun splitToChunks(text: String): List<String> {
        val chunks = mutableListOf<String>()
        if (text.length <= MAX_CHARS_PER_PAGE) {
            chunks.add(text)
            return chunks
        }

        val words = text.split(Regex("(?<=[ 、。！？ ])"))
        var cur = StringBuilder()
        for (w in words) {
            if (cur.length + w.length > MAX_CHARS_PER_PAGE) {
                if (cur.isNotEmpty()) {
                    chunks.add(cur.toString())
                    cur = StringBuilder()
                }
                var remaining = w
                while (remaining.length > MAX_CHARS_PER_PAGE) {
                    chunks.add(remaining.substring(0, MAX_CHARS_PER_PAGE))
                    remaining = remaining.substring(MAX_CHARS_PER_PAGE)
                }
                cur.append(remaining)
            } else {
                cur.append(w)
            }
        }
        if (cur.isNotEmpty()) chunks.add(cur.toString())
        return chunks
    }

    private fun buildAgreePage(): Component {
        return Component.empty()
            .append(Component.text("-------------------\n", NamedTextColor.GRAY))
            .append(Component.text("上記ルールを確認の上、\n下のボタンを押して同意してください。\n\n", NamedTextColor.DARK_GRAY))
            .append(
                Component.text("[ ✔ 同意する ]")
                    .color(NamedTextColor.DARK_GREEN)
                    .decorate(TextDecoration.BOLD)
                    .clickEvent(ClickEvent.runCommand("/rules agree"))
                    .hoverEvent(HoverEvent.showText(Component.text("クリックして同意", NamedTextColor.GREEN)))
            )
    }

    fun openBookForPlayer(player: Player) {
        val pages = cachedPages
        if (pages == null || pages.isEmpty()) {
            RulesListener.sendRulesMessage(player, plugin.config.getString("rules-url", "https://www.vcmc.jp/rules") ?: "https://www.vcmc.jp/rules")
            return
        }

        val title = plugin.config.getString("book-title", "vcmc.jp サーバールール") ?: "vcmc.jp サーバールール"
        val author = plugin.config.getString("book-author", "vcmc.jp") ?: "vcmc.jp"

        val book = Book.book(
            Component.text(title),
            Component.text(author),
            pages
        )

        player.scheduler.run(plugin, { _ ->
            player.openBook(book)
            player.sendMessage(
                Component.text("本を開きました。最後のページで ", NamedTextColor.YELLOW)
                    .append(Component.text("[同意する]", NamedTextColor.GREEN, TextDecoration.BOLD))
                    .append(Component.text(" をクリックしてください。", NamedTextColor.YELLOW))
            )
        }, null)
    }

    fun hasCachedBook(): Boolean = !cachedPages.isNullOrEmpty()

    fun reload() {
        cachedPages = null
        isFetchFailed = false
        fetchAsync()
    }
}
