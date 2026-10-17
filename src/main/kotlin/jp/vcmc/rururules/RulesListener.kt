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
import org.bukkit.event.player.*

class RulesListener(
    private val plugin: RuruRulesPlugin,
    private val rulesManager: RulesManager,
    private val bookFetcher: RulesBookFetcher
) : Listener {

    // プレイヤー参加
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        if (rulesManager.hasAgreed(player)) return

        rulesManager.addPending(player)

        // 1秒後に表示（チャンクロード完了待ち）
        player.scheduler.runDelayed(plugin, { _ -> showRules(player) }, null, 20L)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        rulesManager.removePending(event.player)
    }

    // 移動制限 (視点変更は許可)
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        if (!rulesManager.isPending(player)) return

        val to = event.to ?: run {
            event.isCancelled = true
            return
        }
        val from = event.from
        if (from.blockX != to.blockX || from.blockY != to.blockY || from.blockZ != to.blockZ) {
            event.isCancelled = true
        }
    }

    // インタラクト制限
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        if (!rulesManager.isPending(player)) return
        event.isCancelled = true
        if (rulesManager.isShowMessageOnBlock) showRules(player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        val player = event.player
        if (!rulesManager.isPending(player)) return
        event.isCancelled = true
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

    // チャット制限
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onAsyncChat(event: AsyncChatEvent) {
        val player = event.player
        if (!rulesManager.isPending(player)) return
        event.isCancelled = true
        if (rulesManager.isShowMessageOnBlock) {
            player.scheduler.run(plugin, { _ -> showRules(player) }, null)
        }
    }

    // コマンド制限 (/rules は許可)
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerCommand(event: PlayerCommandPreprocessEvent) {
        val player = event.player
        if (!rulesManager.isPending(player)) return

        val cmd = event.message.lowercase().trim()
        if (!cmd.equals("/rules") && !cmd.startsWith("/rules ") && !cmd.equals("/rule") && !cmd.startsWith("/rule ")) {
            event.isCancelled = true
            if (rulesManager.isShowMessageOnBlock) showRules(player)
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
        if (rulesManager.isPending(event.player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerSwapHandItems(event: PlayerSwapHandItemsEvent) {
        if (rulesManager.isPending(event.player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val who = event.whoClicked
        if (who is Player && rulesManager.isPending(who)) {
            event.isCancelled = true
        }
    }

    /**
     * ルールを表示
     */
    fun showRules(player: Player) {
        val bookMode = plugin.config.getBoolean("show-as-book", true)
        if (bookMode && bookFetcher.hasCachedBook()) {
            bookFetcher.openBookForPlayer(player)
        } else {
            sendRulesMessage(player, plugin.config.getString("rules-url", "https://www.vcmc.jp/rules") ?: "https://www.vcmc.jp/rules")
        }
    }

    companion object {
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
