package jp.vcmc.rururules;

import org.bukkit.plugin.java.JavaPlugin;

public class RuruRulesPlugin extends JavaPlugin {

    private RulesManager rulesManager;
    private RulesBookFetcher bookFetcher;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        rulesManager = new RulesManager(this);
        rulesManager.loadAgreedPlayers();

        bookFetcher = new RulesBookFetcher(this);
        if (getConfig().getBoolean("show-as-book", true)) {
            // 起動時に非同期でルールページを取得
            bookFetcher.fetchAsync();
        }

        getServer().getPluginManager().registerEvents(new RulesListener(this, rulesManager, bookFetcher), this);

        RulesCommand cmd = new RulesCommand(this, rulesManager, bookFetcher);
        getCommand("rules").setExecutor(cmd);
        getCommand("rules").setTabCompleter(cmd);

        getLogger().info("RuruRules が有効化されました。");
    }

    @Override
    public void onDisable() {
        if (rulesManager != null) {
            rulesManager.saveAgreedPlayers();
        }
        getLogger().info("RuruRules が無効化されました。");
    }

    public RulesManager getRulesManager() {
        return rulesManager;
    }

    public RulesBookFetcher getBookFetcher() {
        return bookFetcher;
    }
}
