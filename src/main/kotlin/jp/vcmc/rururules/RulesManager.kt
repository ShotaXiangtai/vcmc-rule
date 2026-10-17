package jp.vcmc.rururules

import org.bukkit.entity.Player
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class RulesManager(private val plugin: RuruRulesPlugin) {

    private val agreedPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val pendingPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val dataFile: Path = plugin.dataFolder.toPath().resolve("agreed_players.txt")

    @Synchronized
    fun loadAgreedPlayers() {
        if (!Files.exists(dataFile)) {
            try {
                dataFile.parent?.let { Files.createDirectories(it) }
                Files.createFile(dataFile)
            } catch (e: IOException) {
                plugin.logger.severe("データファイル作成失敗: ${e.message}")
            }
            return
        }

        try {
            Files.newBufferedReader(dataFile).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line?.trim() ?: continue
                    if (trimmed.isNotEmpty()) {
                        try {
                            agreedPlayers.add(UUID.fromString(trimmed))
                        } catch (e: IllegalArgumentException) {
                            plugin.logger.warning("無効なUUIDをスキップ: $trimmed")
                        }
                    }
                }
            }
        } catch (e: IOException) {
            plugin.logger.severe("同意プレイヤー読み込み失敗: ${e.message}")
        }

        plugin.logger.info("${agreedPlayers.size} 名の同意プレイヤーデータを読み込みました。")
    }

    @Synchronized
    fun saveAgreedPlayers() {
        try {
            dataFile.parent?.let { Files.createDirectories(it) }
            Files.newBufferedWriter(dataFile).use { writer ->
                for (uuid in agreedPlayers) {
                    writer.write(uuid.toString())
                    writer.newLine()
                }
            }
        } catch (e: IOException) {
            plugin.logger.severe("同意プレイヤー保存失敗: ${e.message}")
        }
    }

    fun hasAgreed(uuid: UUID): Boolean = agreedPlayers.contains(uuid)

    fun hasAgreed(player: Player): Boolean = hasAgreed(player.uniqueId)

    fun setAgreed(player: Player) {
        val uuid = player.uniqueId
        agreedPlayers.add(uuid)
        pendingPlayers.remove(uuid)
        plugin.server.asyncScheduler.runNow(plugin) { _ -> saveAgreedPlayers() }
    }

    fun resetAgreement(uuid: UUID) {
        agreedPlayers.remove(uuid)
        plugin.server.asyncScheduler.runNow(plugin) { _ -> saveAgreedPlayers() }
    }

    fun isPending(player: Player): Boolean = pendingPlayers.contains(player.uniqueId)

    fun addPending(player: Player) {
        pendingPlayers.add(player.uniqueId)
    }

    fun removePending(player: Player) {
        pendingPlayers.remove(player.uniqueId)
    }

    val rulesUrl: String
        get() = plugin.config.getString("rules-url", "https://www.vcmc.jp/rules") ?: "https://www.vcmc.jp/rules"

    val isShowMessageOnBlock: Boolean
        get() = plugin.config.getBoolean("show-message-on-block", true)
}
