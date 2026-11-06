package jp.vcmc.rururules

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import jp.vcmc.rururules.i18n.Language
import net.kyori.adventure.inventory.Book
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import org.bukkit.entity.Player
import org.jsoup.Jsoup
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class RulesBookFetcher(private val plugin: RuruRulesPlugin) {

    private companion object {
        const val MAX_BOOK_PAGES = 100
        const val DEFAULT_REFRESH_MINUTES = 60L

        /** 取得に失敗したときは、次の定期更新を待たずにこの間隔でやり直す。 */
        const val RETRY_MINUTES = 5L
    }

    /** 言語ごとのページの内容。取得に失敗しても前回の内容を使い続けるので、取得できたときだけ置き換える。 */
    private val contents = ConcurrentHashMap<Language, List<RuleBlock>>()

    @Volatile
    private var books: Map<Language, List<Component>> = emptyMap()

    private val fetching = AtomicBoolean(false)
    private val nextFetch = AtomicReference<ScheduledTask?>()

    fun fetchAsync() {
        if (!fetching.compareAndSet(false, true)) return
        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            val success = try {
                fetchAll()
            } catch (e: Exception) {
                plugin.logger.warning("ルールページ取得失敗: ${e.message}")
                false
            } finally {
                fetching.set(false)
            }
            scheduleNext(success)
        }
    }

    /** すべての言語のページを取得して本を作り直す。1 つでも失敗したら false を返す。 */
    private fun fetchAll(): Boolean {
        var success = true
        for (language in Language.entries) {
            val url = plugin.rulesManager.pageUrl(language)
            if (url.isBlank()) {
                contents.remove(language)
                continue
            }
            val blocks = fetch(url)
            if (blocks != null) contents[language] = blocks else success = false
        }
        books = Language.entries.mapNotNull { language -> buildBook(language)?.let { language to it } }.toMap()
        return success
    }

    /** ページの内容を返す。取得できなければ null。 */
    private fun fetch(url: String): List<RuleBlock>? {
        val config = plugin.config
        val timeout = config.getInt("fetch-timeout-ms", 10000)
        val selector = config.getString("content-selector", "main, article, .content, #content").orEmpty()
        val exclude = config.getString("exclude-selector", RulesPageParser.DEFAULT_EXCLUDE).orEmpty()

        plugin.logger.info("ルールページ取得開始: $url")
        val blocks = try {
            val document = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (compatible; RuruRules/${plugin.pluginMeta.version})")
                .timeout(timeout)
                .get()
            RulesPageParser.parse(document, selector, exclude)
        } catch (e: Exception) {
            plugin.logger.warning("ルールページ取得失敗 ($url): ${e.message}")
            return null
        }
        if (blocks.isEmpty()) {
            plugin.logger.warning("ルールページのテキスト抽出が空でした ($url)。前回の内容を使います。")
            return null
        }
        plugin.logger.info("ルールページ取得完了 ($url)")
        return blocks
    }

    private fun scheduleNext(success: Boolean) {
        if (!plugin.isEnabled) return
        val interval = plugin.config.getLong("refresh-interval-minutes", DEFAULT_REFRESH_MINUTES)
        val delay = if (success) interval else RETRY_MINUTES
        val task = if (delay > 0) {
            plugin.server.asyncScheduler.runDelayed(plugin, { _ -> fetchAsync() }, delay, TimeUnit.MINUTES)
        } else {
            null
        }
        nextFetch.getAndSet(task)?.cancel()
    }

    /**
     * その言語の本を作る。翻訳ページが無い言語は日本語のページを使い、最初のページで案内する。
     * 日本語のページも取得できていなければ null。
     */
    private fun buildBook(language: Language): List<Component>? {
        val own = contents[language]
        val blocks = own ?: contents[Language.JAPANESE] ?: return null
        val notice = if (own == null) plugin.messages.untranslatedNotice(language) else null
        val texts = BookLayout.paginate(blocks)
        // 最初の案内と、最後の同意ボタンのページの分を空けておく。
        val limit = MAX_BOOK_PAGES - 1 - (if (notice != null) 1 else 0)
        if (texts.size > limit) {
            plugin.logger.warning("ルールが本の上限を超えるため、${limit + 1} ページ目以降を省略します (${language.code}: ${texts.size} ページ)")
        }
        return listOfNotNull(notice) + texts.take(limit).map { Component.text(it) } + buildAgreePage(language)
    }

    private fun buildAgreePage(language: Language): Component {
        val messages = plugin.messages
        return messages.text(language, "agree-page-text").append(
            messages.text(language, "agree-page-button")
                .clickEvent(ClickEvent.runCommand("/rules agree"))
                .hoverEvent(HoverEvent.showText(messages.text(language, "agree-page-hover"))),
        )
    }

    /**
     * 本を開く。hasBook で本があることを確かめてから呼ぶこと。
     *
     * @param reopened 同意せずに閉じられた本を開き直すときは true。
     * @param onOpened 本を開いた直後に、プレイヤーのリージョンで呼ばれる。
     */
    fun openBookForPlayer(player: Player, reopened: Boolean = false, onOpened: () -> Unit = {}) {
        val language = Language.of(player)
        val pages = books[language] ?: return
        val messages = plugin.messages
        val book = Book.book(Component.text(messages.bookTitle(language)), Component.text(messages.bookAuthor(language)), pages)

        player.scheduler.run(plugin, { _ ->
            player.openBook(book)
            messages.send(player, if (reopened) "book-reopened" else "book-opened")
            onOpened()
        }, null)
    }

    fun hasBook(language: Language): Boolean = language in books

    /** 設定の再読み込み後に呼ぶ。本を使わない設定になったら定期更新を止める。 */
    fun reload() {
        nextFetch.getAndSet(null)?.cancel()
        if (plugin.config.getBoolean("show-as-book", true)) fetchAsync()
    }
}
