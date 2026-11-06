package jp.vcmc.rururules

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class RulesCommand(
    private val plugin: RuruRulesPlugin,
    private val rulesManager: RulesManager,
    private val bookFetcher: RulesBookFetcher,
    private val rulesListener: RulesListener,
) : BasicCommand {

    override fun execute(stack: CommandSourceStack, args: Array<out String>) {
        val sender = stack.sender
        if (args.isEmpty()) {
            if (sender is Player) {
                rulesListener.showRules(sender)
            } else {
                sender.sendMessage(Component.text(USAGE, NamedTextColor.YELLOW))
            }
            return
        }

        when (args[0].lowercase()) {
            "agree" -> handleAgree(sender)
            "reload" -> handleReload(sender)
            "reset" -> handleReset(sender, args)
            else -> sender.sendMessage(Component.text(USAGE, NamedTextColor.YELLOW))
        }
    }

    private fun handleAgree(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendMessage(Component.text("このコマンドはプレイヤーのみ使用できます。", NamedTextColor.RED))
            return
        }
        if (rulesManager.hasAgreed(sender)) {
            sender.sendMessage(Component.text("すでにルールに同意済みです。", NamedTextColor.GREEN))
            return
        }
        rulesManager.setAgreed(sender)
        sender.sendMessage(Component.empty())
        sender.sendMessage(Component.text("ルールへの同意を確認しました！", NamedTextColor.GREEN, TextDecoration.BOLD))
        sender.sendMessage(Component.text("vcmc.jp へようこそ！楽しい時間をお過ごしください！", NamedTextColor.YELLOW))
        sender.sendMessage(Component.empty())
        plugin.logger.info("${sender.name} (${sender.uniqueId}) がルールに同意しました。")
    }

    private fun handleReload(sender: CommandSender) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            sender.sendMessage(Component.text("このコマンドを実行する権限がありません。", NamedTextColor.RED))
            return
        }
        plugin.reloadConfig()
        bookFetcher.reload()
        sender.sendMessage(Component.text("設定を再読み込みしました。ルールページはバックグラウンドで取得します。", NamedTextColor.GREEN))
    }

    private fun handleReset(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            sender.sendMessage(Component.text("このコマンドを実行する権限がありません。", NamedTextColor.RED))
            return
        }
        if (args.size < 2) {
            sender.sendMessage(Component.text("使用方法: /rules reset <プレイヤー名>", NamedTextColor.YELLOW))
            return
        }
        val targetName = args[1]
        val target = Bukkit.getPlayerExact(targetName) ?: Bukkit.getOfflinePlayerIfCached(targetName)
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline)) {
            sender.sendMessage(Component.text("プレイヤーが見つかりません: $targetName", NamedTextColor.RED))
            return
        }
        rulesManager.resetAgreement(target.uniqueId)
        val online = Bukkit.getPlayer(target.uniqueId)
        if (online != null) {
            rulesManager.addPending(online)
            online.scheduler.run(plugin, { _ -> rulesListener.showRules(online) }, null)
        }
        sender.sendMessage(Component.text("$targetName の同意状態をリセットしました。", NamedTextColor.GREEN))
    }

    override fun suggest(stack: CommandSourceStack, args: Array<out String>): Collection<String> {
        val sender = stack.sender
        if (args.size <= 1) {
            val prefix = args.firstOrNull()?.lowercase().orEmpty()
            val subcommands = if (sender.hasPermission(PERM_ADMIN)) listOf("agree", "reload", "reset") else listOf("agree")
            return subcommands.filter { it.startsWith(prefix) }
        }
        if (args.size == 2 && args[0].equals("reset", ignoreCase = true) && sender.hasPermission(PERM_ADMIN)) {
            val prefix = args[1].lowercase()
            return Bukkit.getOnlinePlayers().map { it.name }.filter { it.lowercase().startsWith(prefix) }
        }
        return emptyList()
    }

    private companion object {
        const val PERM_ADMIN = "rururules.admin"
        const val USAGE = "使用方法: /rules agree | /rules reload | /rules reset <プレイヤー名>"
    }
}
