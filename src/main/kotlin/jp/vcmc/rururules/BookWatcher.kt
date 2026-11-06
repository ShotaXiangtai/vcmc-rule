package jp.vcmc.rururules

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 同意していないプレイヤーが本を閉じていないかを見張り、閉じていれば開き直す。
 * PlayerMoveEvent は視点が 10 度以上動くまで発火しないので、視点とキーの状態を定期的に読む。
 *
 * @param reopen 閉じられた本を開き直す。プレイヤーのリージョンで呼ばれる。
 */
class BookWatcher(
    private val plugin: RuruRulesPlugin,
    private val rulesManager: RulesManager,
    private val reopen: (Player) -> Unit,
) {
    private val detectors = ConcurrentHashMap<UUID, BookCloseDetector>()

    /** 本を開いた直後に、プレイヤーのリージョンで呼ぶ。同意済みの人が読み返すときは見張らない。 */
    fun onBookOpened(player: Player) {
        if (!rulesManager.isPending(player)) return
        val id = player.uniqueId
        val location = player.location
        val now = System.currentTimeMillis()

        val existing = detectors[id]
        if (existing != null) {
            existing.onOpened(now, location.yaw, location.pitch)
            return
        }

        val detector = BookCloseDetector()
        detector.onOpened(now, location.yaw, location.pitch)
        detectors[id] = detector
        val task = player.scheduler.runAtFixedRate(
            plugin,
            { task -> check(player, detector, task) },
            { detectors.remove(id, detector) },
            CHECK_PERIOD_TICKS,
            CHECK_PERIOD_TICKS,
        )
        if (task == null) detectors.remove(id, detector)
    }

    fun onQuit(player: Player) {
        detectors.remove(player.uniqueId)
    }

    private fun check(player: Player, detector: BookCloseDetector, task: ScheduledTask) {
        if (!rulesManager.isPending(player)) {
            task.cancel()
            detectors.remove(player.uniqueId, detector)
            return
        }
        val location = player.location
        val input = player.currentInput
        val moveKeyPressed = input.isForward || input.isBackward || input.isLeft || input.isRight || input.isJump
        val now = System.currentTimeMillis()
        if (detector.isClosed(now, location.yaw, location.pitch, moveKeyPressed, player.isInsideVehicle)) {
            // 本が開くまでの間にもう一度判定しないよう、ここで開いた扱いにする。
            detector.onOpened(now, location.yaw, location.pitch)
            reopen(player)
        }
    }

    private companion object {
        const val CHECK_PERIOD_TICKS = 5L
    }
}
