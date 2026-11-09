package jp.vcmc.rururules

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import jp.vcmc.rururules.i18n.Language
import net.kyori.adventure.inventory.Book
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import org.bukkit.entity.Player
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
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

        /** 日本語の文が残っているかの目安。仮名があれば翻訳されていない。 */
        val KANA = Regex("[ぁ-ゖァ-ヺ]")
    }

    /** 言語ごとのページの内容。取得に失敗しても前回の内容を使い続けるので、取得できたときだけ置き換える。 */
    private val contents = ConcurrentHashMap<Language, List<RuleBlock>>()

    /** サイトの対訳表。取得に失敗しても前回の表を使い続ける。 */
    @Volatile
    private var translations: SiteTranslations? = null

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

    /**
     * ルールページと対訳表を取得して、すべての言語の本を作り直す。1 つでも失敗したら false を返す。
     * 日本語以外は、rules-urls にその言語のページがあればそれを、無ければ日本語のページを対訳表で翻訳したものを使う。
     */
    private fun fetchAll(): Boolean {
        val manager = plugin.rulesManager
        var success = true

        val translationsUrl = manager.translationsUrl
        if (translationsUrl.isBlank()) {
            translations = null
        } else {
            val loaded = fetchTranslations(translationsUrl)
            if (loaded != null) translations = loaded else success = false
        }

        val japanese = fetchDocument(manager.rulesUrl)
        if (japanese == null) {
            success = false
        } else {
            val blocks = parse(japanese.clone(), manager.rulesUrl)
            if (blocks != null) contents[Language.JAPANESE] = blocks else success = false
        }

        val table = translations
        for (language in Language.entries) {
            if (language == Language.JAPANESE) continue
            val ownUrl = manager.pageUrl(language)
            when {
                ownUrl.isNotBlank() -> {
                    val blocks = fetchDocument(ownUrl)?.let { parse(it, ownUrl) }
                    if (blocks != null) contents[language] = blocks else success = false
                }
                table != null && table.supports(language) -> {
                    // 日本語のページを取得できなかったときは、前回翻訳した内容を使い続ける。
                    if (japanese != null) translate(japanese, table, language)?.let { contents[language] = it }
                }
                else -> contents.remove(language)
            }
        }
        books = Language.entries.mapNotNull { language -> buildBook(language)?.let { language to it } }.toMap()
        return success
    }

    /** 日本語のページを複製して翻訳し、本に載せる文章を返す。 */
    private fun translate(japanese: Document, table: SiteTranslations, language: Language): List<RuleBlock>? {
        val document = japanese.clone()
        table.translate(document, language)
        val blocks = parse(document, "${plugin.rulesManager.rulesUrl}（${language.code} に翻訳）") ?: return null
        val untranslated = blocks.count { KANA.containsMatchIn(it.text) }
        if (untranslated > 0) {
            plugin.logger.info("ルールページの ${language.code} の翻訳: 対訳表に無い $untranslated 個の文は日本語のまま表示します")
        }
        return blocks
    }

    /** ページの HTML を返す。取得できなければ null。 */
    private fun fetchDocument(url: String): Document? {
        plugin.logger.info("ルールページ取得開始: $url")
        return try {
            Jsoup.connect(url)
                .userAgent(userAgent())
                .timeout(plugin.config.getInt("fetch-timeout-ms", 10000))
                .get()
        } catch (e: Exception) {
            plugin.logger.warning("ルールページ取得失敗 ($url): ${e.message}")
            null
        }
    }

    /** 対訳表を返す。取得できないか読めなければ null。 */
    private fun fetchTranslations(url: String): SiteTranslations? =
        try {
            val script = Jsoup.connect(url)
                .userAgent(userAgent())
                .timeout(plugin.config.getInt("fetch-timeout-ms", 10000))
                .ignoreContentType(true)
                .maxBodySize(0)
                .execute()
                .body()
            SiteTranslations.parse(script).also { plugin.logger.info("ルールページの対訳表を取得しました: $url") }
        } catch (e: Exception) {
            plugin.logger.warning("ルールページの対訳表を取得できませんでした ($url): ${e.message}")
            null
        }

    /** 本に載せる文章を取り出す。空なら null。 */
    private fun parse(document: Document, source: String): List<RuleBlock>? {
        val config = plugin.config
        val selector = config.getString("content-selector", "main, article, .content, #content").orEmpty()
        val exclude = config.getString("exclude-selector", RulesPageParser.DEFAULT_EXCLUDE).orEmpty()
        val blocks = RulesPageParser.parse(document, selector, exclude)
        if (blocks.isEmpty()) {
            plugin.logger.warning("ルールページのテキスト抽出が空でした ($source)。前回の内容を使います。")
            return null
        }
        plugin.logger.info("ルールページ取得完了 ($source)")
        return blocks
    }

    private fun userAgent() = "Mozilla/5.0 (compatible; RuruRules/${plugin.pluginMeta.version})"

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
     * その言語の本を作る。翻訳が無い言語は日本語のページを使い、最初のページで案内する。
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
