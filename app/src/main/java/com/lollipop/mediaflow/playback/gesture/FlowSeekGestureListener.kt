package com.lollipop.mediaflow.playback.gesture

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.lollipop.mediaflow.page.flow.compose.gesture.FlowGestureListener

/**
 * Compose 手势源 → 「倍速预览 / 进度拖拽」业务调用（对应 View 侧的
 * [com.lollipop.mediaflow.tools.VideoTouchHelper]，职责相同：做事件与业务的翻译）。
 *
 * 换算逻辑**不重复实现**：直接复用 [TouchSeekMath]（与 View 侧同一份）。
 *
 * @param baseWeight 基础权重（屏幕横移一周代表的比例）。
 * @param xThreshold 进入进度模式的横向阈值（像素）。
 * @param onTapAction 轻触回调（页面做 1/2/3 击判定）。
 */
class FlowSeekGestureListener(
    baseWeight: Float,
    xThreshold: Float,
    private val onStartSpeed: () -> Unit,
    private val onStopSpeed: () -> Unit,
    private val onStartSeek: () -> Unit,
    private val onSeek: (weight: Float, speed: Float) -> Unit,
    private val onStopSeek: (weight: Float) -> Unit,
    private val onTapAction: () -> Unit
) : FlowGestureListener {

    private val math = TouchSeekMath(
        baseWeight = baseWeight,
        xThreshold = xThreshold
    )

    override fun onTap() {
        onTapAction()
    }

    override fun onSingleCapture(size: IntSize, touchDown: Offset, current: Offset) {
        math.capture(
            viewWidth = size.width,
            viewHeight = size.height,
            currentX = current.x
        )
        onStartSpeed()
    }

    override fun onSingleMove(size: IntSize, touchDown: Offset, current: Offset) {
        val update = math.move(
            viewWidth = size.width,
            touchDownX = touchDown.x,
            touchDownY = touchDown.y,
            currentX = current.x,
            currentY = current.y
        ) ?: return
        if (update.startedSeeking) {
            onStartSeek()
        }
        onSeek(update.weight, update.speed)
    }

    override fun onTouchRelease() {
        if (math.release()) {
            onStopSeek(math.accumulatedTimeWeight)
        } else {
            onStopSpeed()
        }
    }
}
