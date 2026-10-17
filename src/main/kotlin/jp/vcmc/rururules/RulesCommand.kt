package jp.vcmc.rururules

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class RulesCommand(
    private val plugin: RuruRulesPlugin,
    private val rulesManager: RulesManager,
    private val bookFetcher: RulesBookFetcher,
    private val rulesListener: RulesListener
) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            if (sender is Player) {
                rulesListener.showRules(sender)
            } else {
                sender.sendMessage("使用方法: /rules agree | /rules reload | /rules reset <プレイヤー名>")
            }
            return true
        }

        return when (args[0].lowercase()) {
            "agree" -> handleAgree(sender)
            "reload" -> handleReload(sender)
            "reset" -> handleReset(sender, args)
            else -> {
                sender.sendMessage(
                    Component.text("使用方法: /rules agree | /rules reload | /rules reset <プレイヤー名>", NamedTextColor.YELLOW)
                )
                true
            }
        }
    }

    private fun handleAgree(sender: CommandSender): Boolean {
        if (sender !is Player) {
            sender.sendMessage("このコマンドはプレイヤーのみ使用できます。")
            return true
        }
        if (rulesManager.hasAgreed(sender)) {
            sender.sendMessage(Component.text("すでにルールに同意済みです。", NamedTextColor.GREEN))
            return true
        }
        rulesManager.setAgreed(sender)
        sender.sendMessage(Component.empty())
        sender.sendMessage(Component.text("ルールへの同意を確認しました！", NamedTextColor.GREEN, TextDecoration.BOLD))
        sender.sendMessage(Component.text("vcmc.jp へようこそ！楽しい時間をお過ごしください！", NamedTextColor.YELLOW))
        sender.sendMessage(Component.empty())
        plugin.logger.info("${sender.name} (${sender.uniqueId}) がルールに同意しました。")
        return true
    }

    private fun handleReload(sender: CommandSender): Boolean {
        if (!sender.hasPermission("rururules.admin")) {
            sender.sendMessage(Component.text("このコマンドを実行する権限がありません。", NamedTextColor.RED))
            return true
        }
        sender.sendMessage(Component.text("ルールページを再取得中...", NamedTextColor.YELLOW))
        plugin.reloadConfig()
        bookFetcher.reload()
        sender.sendMessage(Component.text("設定をリロードしました。", NamedTextColor.GREEN))
        return true
    }

    private fun handleReset(sender: CommandSender, args: Array<out String>): Boolean {
        if (!sender.hasPermission("rururules.admin")) {
            sender.sendMessage(Component.text("このコマンドを実行する権限がありません。", NamedTextColor.RED))
            return true
        }
        if (args.size < 2) {
            sender.sendMessage(Component.text("使用方法: /rules reset <プレイヤー名>", NamedTextColor.YELLOW))
            return true
        }
        val targetName = args[1]
        val target = Bukkit.getPlayerExact(targetName) ?: Bukkit.getOfflinePlayerIfCached(targetName)
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline)) {
            sender.sendMessage(Component.text("プレイヤーが見つかりません: $targetName", NamedTextColor.RED))
            return true
        }
        rulesManager.resetAgreement(target.uniqueId)
        val online = Bukkit.getPlayer(target.uniqueId)
        if (online != null) {
            rulesManager.addPending(online)
            online.scheduler.run(plugin, { _ -> rulesListener.showRules(online) }, null)
        }
        sender.sendMessage(Component.text("$targetName の同意状態をリセットしました。", NamedTextColor.GREEN))
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (args.size == 1) {
            if (sender.hasPermission("rururules.admin")) {
                return listOf("agree", "reload", "reset").filter { it.startsWith(args[0].lowercase()) }
            }
            return listOf("agree").filter { it.startsWith(args[0].lowercase()) }
        }
        if (args.size == 2 && args[0].equals("reset", ignoreCase = true) && sender.hasPermission("rururules.admin")) {
            return Bukkit.getOnlinePlayers().map { it.name }.filter { it.lowercase().startsWith(args[1].lowercase()) }
        }
        return emptyList()
    }
}
