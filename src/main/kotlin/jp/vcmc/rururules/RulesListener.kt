package jp.vcmc.rururules

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class RulesListener(
    private val plugin: RuruRulesPlugin,
    private val rulesManager: RulesManager,
    private val bookFetcher: RulesBookFetcher,
) : Listener {

    private val lastReminder = ConcurrentHashMap<UUID, Long>()
    private val bookWatcher = BookWatcher(plugin, rulesManager, ::reopenBook)

    // プレイヤー参加
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        if (rulesManager.hasAgreed(player)) return

        rulesManager.addPending(player)

        // 1秒後に表示（チャンクロード完了待ち）
        player.scheduler.runDelayed(plugin, { _ ->
            if (rulesManager.isPending(player)) showRules(player)
        }, null, 20L)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        rulesManager.removePending(event.player)
        lastReminder.remove(event.player.uniqueId)
        bookWatcher.onQuit(event.player)
    }

    // 移動制限 (視点変更は許可)
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (rulesManager.isPending(event.player) && event.hasChangedBlock()) {
            event.isCancelled = true
        }
    }

    // インタラクト制限
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        if (!rulesManager.isPending(player)) return
        event.isCancelled = true
        remind(player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        val player = event.player
        if (!rulesManager.isPending(player)) return
        event.isCancelled = true
        remind(player)
    }

    /** 防具立てなどの操作はこちらのイベントになり、上のリスナーには届かない。 */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerInteractAtEntity(event: PlayerInteractAtEntityEvent) {
        val player = event.player
        if (!rulesManager.isPending(player)) return
        event.isCancelled = true
        remind(player)
    }

    // ダメージ・攻撃制限（未同意プレイヤーの被ダメージおよび攻撃を防止）
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageEvent) {
        val entity = event.entity
        if (entity is Player && rulesManager.isPending(entity)) {
            event.isCancelled = true
            return
        }
        if (event is EntityDamageByEntityEvent) {
            val damager = event.damager
            if (damager is Player && rulesManager.isPending(damager)) {
                event.isCancelled = true
                remind(damager)
            }
        }
    }

    // ブロック操作制限
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (rulesManager.isPending(event.player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (rulesManager.isPending(event.player)) {
            event.isCancelled = true
        }
    }

    /**
     * チャット制限。
     * 近距離チャットのプラグインより先に止めないと、未同意のプレイヤーの発言が配られてしまうので LOWEST で動かす。
     */
    @EventHandler(priority = EventPriority.LOWEST)
    fun onAsyncChat(event: AsyncChatEvent) {
        val player = event.player
        if (!rulesManager.isPending(player)) return
        event.isCancelled = true
        player.scheduler.run(plugin, { _ -> remind(player) }, null)
    }

    // コマンド制限 (/rules は許可)
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerCommand(event: PlayerCommandPreprocessEvent) {
        val player = event.player
        if (!rulesManager.isPending(player)) return

        val cmd = event.message.lowercase().trim()
        if (cmd != "/rules" && !cmd.startsWith("/rules ") && cmd != "/rule" && !cmd.startsWith("/rule ")) {
            event.isCancelled = true
            remind(player)
        }
    }

    // アイテム関連制限
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityPickupItem(event: EntityPickupItemEvent) {
        val entity = event.entity
        if (entity is Player && rulesManager.isPending(entity)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        val player = event.player
        if (!rulesManager.isPending(player)) return
        event.isCancelled = true
        remind(player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerSwapHandItems(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        if (!rulesManager.isPending(player)) return
        event.isCancelled = true
        remind(player)
    }

    /** ホットバーの切り替えは止めないが、本を開いたままではできないので、本を閉じた合図になる。 */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerItemHeld(event: PlayerItemHeldEvent) {
        if (rulesManager.isPending(event.player)) remind(event.player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val who = event.whoClicked
        if (who is Player && rulesManager.isPending(who)) {
            event.isCancelled = true
            remind(who)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val who = event.whoClicked
        if (who is Player && rulesManager.isPending(who)) {
            event.isCancelled = true
            remind(who)
        }
    }

    /** 操作を止めたときの再表示。感圧板の上などでは毎 tick 発生するので、間隔を空ける。プレイヤーのリージョンで呼ぶこと。 */
    private fun remind(player: Player) {
        if (!rulesManager.isShowMessageOnBlock) return
        val previous = lastReminder[player.uniqueId]
        if (previous != null && System.currentTimeMillis() - previous < REMIND_INTERVAL_MILLIS) return
        showRules(player)
    }

    /**
     * ルールを表示
     */
    fun showRules(player: Player) {
        lastReminder[player.uniqueId] = System.currentTimeMillis()
        if (isBookMode()) {
            bookFetcher.openBookForPlayer(player) { bookWatcher.onBookOpened(player) }
        } else {
            sendRulesMessage(player, rulesManager.rulesUrl)
        }
    }

    /** 同意せずに閉じられた本を開き直す。本を使っていないときは、チャットが埋まらないよう何もしない。 */
    private fun reopenBook(player: Player) {
        if (!rulesManager.isShowMessageOnBlock || !isBookMode()) return
        lastReminder[player.uniqueId] = System.currentTimeMillis()
        bookFetcher.openBookForPlayer(player, reopened = true) { bookWatcher.onBookOpened(player) }
    }

    private fun isBookMode(): Boolean = plugin.config.getBoolean("show-as-book", true) && bookFetcher.hasCachedBook()

    companion object {
        private const val REMIND_INTERVAL_MILLIS = 3000L

        fun sendRulesMessage(player: Player, url: String) {
            val separator = Component.text("=".repeat(26)).color(NamedTextColor.GOLD)
            val title = Component.text("  【!! ルール確認が必要です !!】  ", NamedTextColor.YELLOW, TextDecoration.BOLD)
            val desc = Component.text("サーバーを利用するには、サーバールールを確認し同意する必要があります。\n下記リンクからルールをご確認ください。", NamedTextColor.WHITE)
            val link = Component.text(" >> $url << ")
                .color(NamedTextColor.AQUA)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(Component.text("クリックしてブラウザで開く", NamedTextColor.GREEN)))
            val agreeBtn = Component.text("[ ✔ ルールに同意する ]")
                .color(NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/rules agree"))
                .hoverEvent(HoverEvent.showText(Component.text("クリックして同意", NamedTextColor.YELLOW)))

            player.sendMessage(Component.empty())
            player.sendMessage(separator)
            player.sendMessage(title)
            player.sendMessage(separator)
            player.sendMessage(Component.empty())
            player.sendMessage(desc)
            player.sendMessage(Component.empty())
            player.sendMessage(link)
            player.sendMessage(Component.empty())
            player.sendMessage(agreeBtn)
            player.sendMessage(Component.empty())
            player.sendMessage(separator)
            player.sendMessage(Component.empty())
        }
    }
}
