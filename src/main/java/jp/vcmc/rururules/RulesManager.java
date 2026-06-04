package jp.vcmc.rururules;

import org.bukkit.entity.Player;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RulesManager {

    private final RuruRulesPlugin plugin;
    private final Set<UUID> agreedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingPlayers = ConcurrentHashMap.newKeySet();
    private final Path dataFile;

    public RulesManager(RuruRulesPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = plugin.getDataFolder().toPath().resolve("agreed_players.txt");
    }

    public void loadAgreedPlayers() {
        if (!Files.exists(dataFile)) {
            try {
                Files.createDirectories(dataFile.getParent());
                Files.createFile(dataFile);
            } catch (IOException e) {
                plugin.getLogger().severe("データファイルの作成に失敗しました: " + e.getMessage());
            }
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(dataFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    try {
                        agreedPlayers.add(UUID.fromString(line));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("無効なUUIDをスキップ: " + line);
                    }
                }
            }
        } catch (IOException e) {
            plugin.getLogger().severe("同意済みプレイヤーの読み込みに失敗しました: " + e.getMessage());
        }

        plugin.getLogger().info(agreedPlayers.size() + " 人のプレイヤーの同意データを読み込みました。");
    }

    public void saveAgreedPlayers() {
        try {
            Files.createDirectories(dataFile.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(dataFile)) {
                for (UUID uuid : agreedPlayers) {
                    writer.write(uuid.toString());
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            plugin.getLogger().severe("同意済みプレイヤーの保存に失敗しました: " + e.getMessage());
        }
    }

    public boolean hasAgreed(UUID uuid) {
        return agreedPlayers.contains(uuid);
    }

    public boolean hasAgreed(Player player) {
        return hasAgreed(player.getUniqueId());
    }

    public void setAgreed(Player player) {
        UUID uuid = player.getUniqueId();
        agreedPlayers.add(uuid);
        pendingPlayers.remove(uuid);
        // 非同期で保存
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> saveAgreedPlayers());
    }

    public void resetAgreement(UUID uuid) {
        agreedPlayers.remove(uuid);
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> saveAgreedPlayers());
    }

    public boolean isPending(Player player) {
        return pendingPlayers.contains(player.getUniqueId());
    }

    public void addPending(Player player) {
        pendingPlayers.add(player.getUniqueId());
    }

    public void removePending(Player player) {
        pendingPlayers.remove(player.getUniqueId());
    }

    public String getRulesUrl() {
        return plugin.getConfig().getString("rules-url", "https://www.vcmc.jp/rules");
    }

    public boolean isShowMessageOnBlock() {
        return plugin.getConfig().getBoolean("show-message-on-block", true);
    }
}
