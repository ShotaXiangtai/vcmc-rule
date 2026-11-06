package jp.vcmc.rururules

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class RulesCommand(
    private val plugin: RuruRulesPlugin,
    private val rulesManager: RulesManager,
    private val bookFetcher: RulesBookFetcher,
    private val rulesListener: RulesListener,
) : BasicCommand {

    private val messages get() = plugin.messages

    override fun execute(stack: CommandSourceStack, args: Array<out String>) {
        val sender = stack.sender
        if (args.isEmpty()) {
            if (sender is Player) {
                rulesListener.showRules(sender)
            } else {
                messages.send(sender, "usage")
            }
            return
        }

        when (args[0].lowercase()) {
            "agree" -> handleAgree(sender)
            "reload" -> handleReload(sender)
            "reset" -> handleReset(sender, args)
            else -> messages.send(sender, "usage")
        }
    }

    private fun handleAgree(sender: CommandSender) {
        if (sender !is Player) {
            messages.send(sender, "player-only")
            return
        }
        if (rulesManager.hasAgreed(sender)) {
            messages.send(sender, "already-agreed")
            return
        }
        rulesManager.setAgreed(sender)
        messages.send(sender, "agreed-title")
        messages.send(sender, "agreed-welcome")
        plugin.logger.info("${sender.name} (${sender.uniqueId}) がルールに同意しました。")
    }

    private fun handleReload(sender: CommandSender) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            messages.send(sender, "no-permission")
            return
        }
        plugin.reloadConfig()
        messages.reload()
        bookFetcher.reload()
        messages.send(sender, "reloaded")
    }

    private fun handleReset(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            messages.send(sender, "no-permission")
            return
        }
        if (args.size < 2) {
            messages.send(sender, "usage-reset")
            return
        }
        val targetName = args[1]
        val target = Bukkit.getPlayerExact(targetName) ?: Bukkit.getOfflinePlayerIfCached(targetName)
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline)) {
            messages.send(sender, "player-not-found", "player" to targetName)
            return
        }
        rulesManager.resetAgreement(target.uniqueId)
        val online = Bukkit.getPlayer(target.uniqueId)
        if (online != null) {
            rulesManager.addPending(online)
            online.scheduler.run(plugin, { _ -> rulesListener.showRules(online) }, null)
        }
        messages.send(sender, "reset-done", "player" to targetName)
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
    }
}
