package jp.vcmc.rururules;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class RulesCommand implements CommandExecutor, TabCompleter {

    private final RuruRulesPlugin plugin;
    private final RulesManager rulesManager;
    private final RulesBookFetcher bookFetcher;

    public RulesCommand(RuruRulesPlugin plugin, RulesManager rulesManager, RulesBookFetcher bookFetcher) {
        this.plugin = plugin;
        this.rulesManager = rulesManager;
        this.bookFetcher = bookFetcher;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // サブコマンドなし
        if (args.length == 0) {
            if (sender instanceof Player player) {
                new RulesListener(plugin, rulesManager, bookFetcher).showRules(player);
            } else {
                sender.sendMessage("使用方法: /rules agree | /rules reload | /rules reset <名前>");
            }
            return true;
        }

        return switch (args[0].toLowerCase()) {

            // /rules agree
            case "agree" -> handleAgree(sender);

            // /rules reload  ← OP専用: ルールページを再取得
            case "reload" -> handleReload(sender);

            // /rules reset <player>  ← OP専用
            case "reset" -> handleReset(sender, args);

            default -> {
                sender.sendMessage(Component.text(
                        "使用方法: /rules agree | /rules reload | /rules reset <名前>",
                        NamedTextColor.YELLOW));
                yield true;
            }
        };
    }

    private boolean handleAgree(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("このコマンドはプレイヤーのみ使用できます。");
            return true;
        }
        if (rulesManager.hasAgreed(player)) {
            player.sendMessage(Component.text("あなたはすでにルールに同意済みです。", NamedTextColor.GREEN));
            return true;
        }
        rulesManager.setAgreed(player);
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("ルールへの同意ありがとうございます！", NamedTextColor.GREEN, TextDecoration.BOLD));
        player.sendMessage(Component.text("ようこそ、vcmc.jp へ！", NamedTextColor.YELLOW));
        player.sendMessage(Component.empty());
        plugin.getLogger().info(player.getName() + " (" + player.getUniqueId() + ") がルールに同意しました。");
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("rururules.admin")) {
            sender.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
            return true;
        }
        sender.sendMessage(Component.text("ルールページを再取得します...", NamedTextColor.YELLOW));
        bookFetcher.reload();
        plugin.reloadConfig();
        return true;
    }

    private boolean handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rururules.admin")) {
            sender.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("使用方法: /rules reset <プレイヤー名>", NamedTextColor.YELLOW));
            return true;
        }
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(Component.text("プレイヤーが見つかりません: " + args[1], NamedTextColor.RED));
            return true;
        }
        rulesManager.resetAgreement(target.getUniqueId());
        Player online = Bukkit.getPlayer(target.getUniqueId());
        if (online != null) {
            rulesManager.addPending(online);
            new RulesListener(plugin, rulesManager, bookFetcher).showRules(online);
        }
        sender.sendMessage(Component.text(args[1] + " の同意状態をリセットしました。", NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            if (sender.hasPermission("rururules.admin")) {
                return List.of("agree", "reload", "reset");
            }
            return List.of("agree");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reset") && sender.hasPermission("rururules.admin")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
