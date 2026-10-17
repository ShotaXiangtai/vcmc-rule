package jp.vcmc.rururules

import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin

class RuruRulesPlugin : JavaPlugin() {

    lateinit var rulesManager: RulesManager
        private set

    lateinit var bookFetcher: RulesBookFetcher
        private set

    override fun onEnable() {
        saveDefaultConfig()

        rulesManager = RulesManager(this)
        rulesManager.loadAgreedPlayers()

        bookFetcher = RulesBookFetcher(this)
        if (config.getBoolean("show-as-book", true)) {
            bookFetcher.fetchAsync()
        }

        val rulesListener = RulesListener(this, rulesManager, bookFetcher)
        server.pluginManager.registerEvents(rulesListener, this)

        val cmd = RulesCommand(this, rulesManager, bookFetcher, rulesListener)
        val bcmd = object : Command("rules") {
            override fun execute(s: CommandSender, l: String, a: Array<out String>): Boolean {
                return cmd.onCommand(s, this, l, a)
            }

            override fun tabComplete(s: CommandSender, l: String, a: Array<out String>): List<String> {
                return cmd.onTabComplete(s, this, l, a)
            }
        }
        bcmd.aliases = listOf("rule")
        server.commandMap.register(name, bcmd)

        logger.info("RuruRules 有効化完了。")
    }

    override fun onDisable() {
        if (::rulesManager.isInitialized) {
            rulesManager.saveAgreedPlayers()
        }
        logger.info("RuruRules 無効化完了。")
    }
}
