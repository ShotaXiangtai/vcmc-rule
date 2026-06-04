package jp.vcmc.rururules;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;

public class RulesListener implements Listener {

    private final RuruRulesPlugin plugin;
    private final RulesManager rulesManager;
    private final RulesBookFetcher bookFetcher;

    public RulesListener(RuruRulesPlugin plugin, RulesManager rulesManager, RulesBookFetcher bookFetcher) {
        this.plugin = plugin;
        this.rulesManager = rulesManager;
        this.bookFetcher = bookFetcher;
    }

    // ─── 参加時 ───────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (rulesManager.hasAgreed(player)) return;

        rulesManager.addPending(player);

        // 1秒後にルール表示 (チャンクロード完了を待つ)
        player.getScheduler().runDelayed(plugin, task -> showRules(player), null, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        rulesManager.removePending(event.getPlayer());
    }

    // ─── 移動ブロック (位置変化のみ。視点移動は許可) ──────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!rulesManager.isPending(player)) return;

        if (event.getTo() == null) {
            event.setCancelled(true);
            return;
        }
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            event.setCancelled(true);
        }
    }

    // ─── インタラクション系 ────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!rulesManager.isPending(player)) return;
        event.setCancelled(true);
        if (rulesManager.isShowMessageOnBlock()) showRules(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!rulesManager.isPending(player)) return;
        event.setCancelled(true);
    }

    // ─── ブロック操作 ──────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (rulesManager.isPending(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (rulesManager.isPending(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    // ─── チャット ──────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!rulesManager.isPending(player)) return;
        event.setCancelled(true);
        if (rulesManager.isShowMessageOnBlock()) {
            plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> showRules(player));
        }
    }

    // ─── コマンド制限 (/rules のみ許可) ───────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!rulesManager.isPending(player)) return;

        String cmd = event.getMessage().toLowerCase().trim();
        if (!cmd.equals("/rules") && !cmd.startsWith("/rules ") && !cmd.equals("/rule") && !cmd.startsWith("/rule ")) {
            event.setCancelled(true);
            if (rulesManager.isShowMessageOnBlock()) showRules(player);
        }
    }

    // ─── アイテム系 ────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && rulesManager.isPending(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (rulesManager.isPending(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && rulesManager.isPending(player)) {
            event.setCancelled(true);
        }
    }

    // ─── ルール表示ディスパッチ ────────────────────────────────

    /**
     * 本モードが有効でキャッシュ済みなら本を開く。
     * それ以外はチャットにURLを表示する。
     */
    public void showRules(Player player) {
        boolean bookMode = plugin.getConfig().getBoolean("show-as-book", true);
        if (bookMode && bookFetcher.hasCachedBook()) {
            bookFetcher.openBookForPlayer(player);
        } else {
            sendRulesMessage(player, plugin.getConfig().getString("rules-url", "https://www.vcmc.jp/rules"));
        }
    }

    // ─── チャット表示 (フォールバック / 本モードOFF時) ──────────

    public static void sendRulesMessage(Player player, String url) {
        Component separator = Component.text("━".repeat(26)).color(NamedTextColor.GOLD);
        Component title = Component.text("  !! ルール確認が必要です !!  ", NamedTextColor.YELLOW, TextDecoration.BOLD);
        Component desc = Component.text("このサーバーで遊ぶには、サーバールールへの\n同意が必要です。", NamedTextColor.WHITE);
        Component link = Component.text(" >> " + url + " << ")
                .color(NamedTextColor.AQUA)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(
                        Component.text("クリックしてブラウザでルールを開く", NamedTextColor.GREEN)
                ));
        Component agreeBtn = Component.text("[ ✔  ルールを読んで同意する ]")
                .color(NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/rules agree"))
                .hoverEvent(HoverEvent.showText(
                        Component.text("ルールを読んだらクリックしてください", NamedTextColor.YELLOW)
                ));

        player.sendMessage(Component.empty());
        player.sendMessage(separator);
        player.sendMessage(title);
        player.sendMessage(separator);
        player.sendMessage(Component.empty());
        player.sendMessage(desc);
        player.sendMessage(Component.empty());
        player.sendMessage(link);
        player.sendMessage(Component.empty());
        player.sendMessage(agreeBtn);
        player.sendMessage(Component.empty());
        player.sendMessage(separator);
        player.sendMessage(Component.empty());
    }
}
