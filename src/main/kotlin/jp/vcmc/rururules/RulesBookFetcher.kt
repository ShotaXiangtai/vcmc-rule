package jp.vcmc.rururules

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import net.kyori.adventure.inventory.Book
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.entity.Player
import org.jsoup.Jsoup
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

    /** 取得に失敗しても前回の内容を使い続けるので、新しい内容を取得できたときだけ置き換える。 */
    @Volatile
    private var cachedPages: List<Component>? = null

    private val fetching = AtomicBoolean(false)
    private val nextFetch = AtomicReference<ScheduledTask?>()

    fun fetchAsync() {
        if (!fetching.compareAndSet(false, true)) return
        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            val success = try {
                fetchOnce()
            } catch (e: Exception) {
                plugin.logger.warning("ルールページ取得失敗: ${e.message}")
                false
            } finally {
                fetching.set(false)
            }
            scheduleNext(success)
        }
    }

    private fun fetchOnce(): Boolean {
        val config = plugin.config
        val url = plugin.rulesManager.rulesUrl
        val timeout = config.getInt("fetch-timeout-ms", 10000)
        val selector = config.getString("content-selector", "main, article, .content, #content").orEmpty()
        val exclude = config.getString("exclude-selector", RulesPageParser.DEFAULT_EXCLUDE).orEmpty()

        plugin.logger.info("ルールページ取得開始: $url")
        val document = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (compatible; RuruRules/1.1)")
            .timeout(timeout)
            .get()

        val blocks = RulesPageParser.parse(document, selector, exclude)
        if (blocks.isEmpty()) {
            plugin.logger.warning("ルールページのテキスト抽出が空でした。前回の内容かチャット表示を使います。")
            return false
        }

        val pages = buildBookPages(blocks)
        cachedPages = pages
        plugin.logger.info("ルールページ取得完了 (${pages.size} ページ)")
        return true
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

    private fun buildBookPages(blocks: List<RuleBlock>): List<Component> {
        val texts = BookLayout.paginate(blocks)
        // 最後のページは同意ボタン用に空けておく。
        val limit = MAX_BOOK_PAGES - 1
        if (texts.size > limit) {
            plugin.logger.warning("ルールが本の上限を超えるため、${limit + 1} ページ目以降を省略します (${texts.size} ページ)")
        }
        return texts.take(limit).map { Component.text(it) } + buildAgreePage()
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
                    .hoverEvent(HoverEvent.showText(Component.text("クリックして同意", NamedTextColor.GREEN))),
            )
    }

    fun openBookForPlayer(player: Player) {
        val pages = cachedPages
        if (pages.isNullOrEmpty()) {
            RulesListener.sendRulesMessage(player, plugin.rulesManager.rulesUrl)
            return
        }

        val config = plugin.config
        val title = config.getString("book-title", "vcmc.jp サーバールール") ?: "vcmc.jp サーバールール"
        val author = config.getString("book-author", "vcmc.jp") ?: "vcmc.jp"
        val book = Book.book(Component.text(title), Component.text(author), pages)

        player.scheduler.run(plugin, { _ ->
            player.openBook(book)
            player.sendMessage(
                Component.text("本を開きました。最後のページで ", NamedTextColor.YELLOW)
                    .append(Component.text("[同意する]", NamedTextColor.GREEN, TextDecoration.BOLD))
                    .append(Component.text(" をクリックしてください。", NamedTextColor.YELLOW)),
            )
        }, null)
    }

    fun hasCachedBook(): Boolean = !cachedPages.isNullOrEmpty()

    /** 設定の再読み込み後に呼ぶ。本を使わない設定になったら定期更新を止める。 */
    fun reload() {
        nextFetch.getAndSet(null)?.cancel()
        if (plugin.config.getBoolean("show-as-book", true)) fetchAsync()
    }
}
