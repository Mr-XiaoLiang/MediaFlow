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
 * @param yMaxRangeRatio 纵向拉到 `min(width, height) × 该比例` 时达到最精细（偏好可调，见
 *   `Preferences.videoTouchMaxRangeRatioY`；与 View 侧 `VideoTouchHelper` 保持同一入口）。
 * @param onTapAction 轻触回调（页面做 1/2/3 击判定）。
 */
class FlowSeekGestureListener(
    baseWeight: Float,
    xThreshold: Float,
    yMaxRangeRatio: Float = TouchSeekMathDefaultYMaxRangeRatio,
    private val onStartSpeed: () -> Unit,
    private val onStopSpeed: () -> Unit,
    private val onStartSeek: () -> Unit,
    private val onSeek: (weight: Float, speed: Float) -> Unit,
    private val onStopSeek: (weight: Float) -> Unit,
    private val onTapAction: (x: Float) -> Unit
) : FlowGestureListener {

    private val math = TouchSeekMath(
        baseWeight = baseWeight,
        xThreshold = xThreshold,
        yMaxRangeRatio = yMaxRangeRatio
    )

    override fun onTap(position: Offset) {
        onTapAction(position.x)
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
            // 对齐 View 版 `VideoTouchHelper.onSwitchToSeekMode()`：先结束「倍速预览」，
            // 再进入触摸 seek。否则长按进入的加速不会恢复——速度会一直停在快速档，
            // 且下一次长按记录的「进入前速度」已是快速档，表现为"松手也不释放"。
            onStopSpeed()
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

/** 纵向精度范围比例的缺省值（与 [TouchSeekMath] 的默认参数一致）。 */
private const val TouchSeekMathDefaultYMaxRangeRatio = 0.5F
