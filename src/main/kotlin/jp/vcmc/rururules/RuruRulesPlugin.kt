package jp.vcmc.rururules

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.plugin.java.JavaPlugin

class RuruRulesPlugin : JavaPlugin() {

    lateinit var messages: Messages
        private set

    lateinit var rulesManager: RulesManager
        private set

    lateinit var bookFetcher: RulesBookFetcher
        private set

    override fun onEnable() {
        saveDefaultConfig()

        messages = Messages(this)
        messages.reload()

        rulesManager = RulesManager(this)
        rulesManager.loadAgreedPlayers()

        bookFetcher = RulesBookFetcher(this)
        if (config.getBoolean("show-as-book", true)) {
            bookFetcher.fetchAsync()
        }

        val rulesListener = RulesListener(this, rulesManager, bookFetcher)
        server.pluginManager.registerEvents(rulesListener, this)

        val command = RulesCommand(this, rulesManager, bookFetcher, rulesListener)
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar().register("rules", "サーバールールを表示して同意します", listOf("rule"), command)
        }

        logger.info("RuruRules 有効化完了。")
    }

    override fun onDisable() {
        if (::rulesManager.isInitialized) {
            rulesManager.saveAgreedPlayers()
        }
        logger.info("RuruRules 無効化完了。")
    }
}
