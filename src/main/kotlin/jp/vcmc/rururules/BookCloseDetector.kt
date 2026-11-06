package jp.vcmc.rururules

import kotlin.math.abs

/**
 * ルールの本が閉じられたかどうかの判定。1 人のプレイヤーのリージョンからだけ使うこと。
 *
 * 本を閉じてもクライアントはサーバーへ何も送らないので、閉じたことは直接には分からない。
 * 本を開いている間はマウスで視点を動かせず、移動キーも効かないので、
 * 視点が動くか移動キーが押されたら、本はもう閉じられていると判断する。
 */
class BookCloseDetector(private val graceMillis: Long = DEFAULT_GRACE_MILLIS) {
    private var openedAtMillis = 0L
    private var yaw = 0f
    private var pitch = 0f

    /** 本を開いた直後に呼ぶ。 */
    fun onOpened(nowMillis: Long, yaw: Float, pitch: Float) {
        openedAtMillis = nowMillis
        remember(yaw, pitch)
    }

    /**
     * @param rotationFollowsVehicle 乗り物に乗っているか。本を開いたままでも乗り物と一緒に視点が回るので、視点では判断しない。
     */
    fun isClosed(
        nowMillis: Long,
        yaw: Float,
        pitch: Float,
        moveKeyPressed: Boolean,
        rotationFollowsVehicle: Boolean = false,
    ): Boolean {
        // 本を開く前にクライアントが送った視点やキーの情報は遅れて届く。読んでいる人の本を開き直さないよう、この間は判断しない。
        if (nowMillis - openedAtMillis < graceMillis) {
            remember(yaw, pitch)
            return false
        }
        if (rotationFollowsVehicle) {
            remember(yaw, pitch)
            return moveKeyPressed
        }
        return moveKeyPressed || abs(yaw - this.yaw) > ROTATION_EPSILON || abs(pitch - this.pitch) > ROTATION_EPSILON
    }

    private fun remember(yaw: Float, pitch: Float) {
        this.yaw = yaw
        this.pitch = pitch
    }

    companion object {
        const val DEFAULT_GRACE_MILLIS = 1000L

        /** これより小さい差は視点が動いたとみなさない（度）。 */
        const val ROTATION_EPSILON = 0.1f
    }
}
