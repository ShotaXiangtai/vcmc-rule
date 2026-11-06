package jp.vcmc.rururules

import jp.vcmc.rururules.i18n.Language
import org.bukkit.entity.Player
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class RulesManager(private val plugin: RuruRulesPlugin) {

    private val agreedPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val pendingPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val dataFile: Path = plugin.dataFolder.toPath().resolve("agreed_players.txt")

    @Synchronized
    fun loadAgreedPlayers() {
        if (!Files.exists(dataFile)) return

        try {
            Files.readAllLines(dataFile).map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
                try {
                    agreedPlayers.add(UUID.fromString(line))
                } catch (_: IllegalArgumentException) {
                    plugin.logger.warning("無効なUUIDをスキップ: $line")
                }
            }
        } catch (e: IOException) {
            plugin.logger.severe("同意プレイヤー読み込み失敗: ${e.message}")
        }

        plugin.logger.info("${agreedPlayers.size} 名の同意プレイヤーデータを読み込みました。")
    }

    /** 途中で落ちても全員の同意が消えないよう、一時ファイルに書いてから置き換える。 */
    @Synchronized
    fun saveAgreedPlayers() {
        try {
            dataFile.parent?.let { Files.createDirectories(it) }
            val temp = dataFile.resolveSibling("${dataFile.fileName}.tmp")
            Files.write(temp, agreedPlayers.map { it.toString() })
            try {
                Files.move(temp, dataFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, dataFile, StandardCopyOption.REPLACE_EXISTING)
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

    /** その言語のルールページの URL。翻訳ページが無ければ空文字列。 */
    fun pageUrl(language: Language): String =
        if (language == Language.JAPANESE) rulesUrl else plugin.config.getString("rules-urls.${language.code}").orEmpty()

    /** チャットに出すリンクの URL。翻訳ページが無ければ日本語のページ。 */
    fun linkUrl(language: Language): String = pageUrl(language).ifBlank { rulesUrl }

    val isShowMessageOnBlock: Boolean
        get() = plugin.config.getBoolean("show-message-on-block", true)
}
