package jp.vcmc.rururules

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookCloseDetectorTest {
    private val openedAt = 10_000L
    private val afterGrace = openedAt + BookCloseDetector.DEFAULT_GRACE_MILLIS
    private val detector = BookCloseDetector().apply { onOpened(openedAt, 90f, 10f) }

    @Test
    fun `読んでいる間は何も動かないので閉じたと判定しない`() {
        assertFalse(detector.isClosed(afterGrace, 90f, 10f, moveKeyPressed = false))
        assertFalse(detector.isClosed(afterGrace + 60_000, 90f, 10f, moveKeyPressed = false))
    }

    @Test
    fun `視点が左右に動いたら閉じたと判定する`() {
        assertTrue(detector.isClosed(afterGrace, 91f, 10f, moveKeyPressed = false))
    }

    @Test
    fun `視点が上下に動いたら閉じたと判定する`() {
        assertTrue(detector.isClosed(afterGrace, 90f, 9f, moveKeyPressed = false))
    }

    @Test
    fun `移動キーを押したら閉じたと判定する`() {
        assertTrue(detector.isClosed(afterGrace, 90f, 10f, moveKeyPressed = true))
    }

    @Test
    fun `計算の誤差ほどの差では閉じたと判定しない`() {
        assertFalse(detector.isClosed(afterGrace, 90.01f, 10.01f, moveKeyPressed = false))
    }

    @Test
    fun `開いた直後に遅れて届いた入力では閉じたと判定しない`() {
        assertFalse(detector.isClosed(openedAt + 100, 95f, 12f, moveKeyPressed = true))
    }

    @Test
    fun `開いた直後に遅れて届いた視点を基準にする`() {
        detector.isClosed(openedAt + 100, 95f, 12f, moveKeyPressed = false)
        assertFalse(detector.isClosed(afterGrace, 95f, 12f, moveKeyPressed = false))
        assertTrue(detector.isClosed(afterGrace, 96f, 12f, moveKeyPressed = false))
    }

    @Test
    fun `開き直すと猶予が始まり、そのときの視点が基準になる`() {
        assertTrue(detector.isClosed(afterGrace, 120f, 10f, moveKeyPressed = false))
        detector.onOpened(afterGrace, 120f, 10f)
        assertFalse(detector.isClosed(afterGrace + 100, 121f, 10f, moveKeyPressed = true))
        assertFalse(detector.isClosed(afterGrace + BookCloseDetector.DEFAULT_GRACE_MILLIS, 121f, 10f, moveKeyPressed = false))
    }

    @Test
    fun `乗り物と一緒に視点が回っても閉じたと判定しない`() {
        assertFalse(detector.isClosed(afterGrace, 180f, 10f, moveKeyPressed = false, rotationFollowsVehicle = true))
    }

    @Test
    fun `乗り物に乗っていても移動キーで閉じたと判定する`() {
        assertTrue(detector.isClosed(afterGrace, 90f, 10f, moveKeyPressed = true, rotationFollowsVehicle = true))
    }

    @Test
    fun `乗り物から降りた直後は降りた時点の視点を基準にする`() {
        detector.isClosed(afterGrace, 180f, 10f, moveKeyPressed = false, rotationFollowsVehicle = true)
        assertFalse(detector.isClosed(afterGrace + 1, 180f, 10f, moveKeyPressed = false))
    }
}
