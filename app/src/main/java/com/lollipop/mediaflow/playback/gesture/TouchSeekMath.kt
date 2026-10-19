package com.lollipop.mediaflow.playback.gesture

import kotlin.math.absoluteValue
import kotlin.math.min

/**
 * 手势进度换算数学（**与事件来源无关**）。
 *
 * 算法从 [com.lollipop.mediaflow.tools.VideoTouchHelper] 原样抽出，语义不做任何改动，
 * 目的只有一个：让 View 手势源（旧外壳）与 Compose 手势源（新外壳）**共用同一份换算逻辑**，
 * 而不是各写一遍。
 *
 * 换算规则：
 * - 横向位移超过 [xThreshold] 才进入进度模式（之前只是"按住"）；
 * - 之后按帧增量累加：`Δweight = (Δx / 宽度) × baseWeight × precision`；
 * - 纵向拉得越远 `precision` 越低（丝滑降速），到 `min(viewW, viewH) × yMaxRangeRatio` 处达到
 *   [minWeight]（最精细），用于微调进度。
 */
class TouchSeekMath(
    /** 基础权重：屏幕横移一个宽度代表 `baseWeight` 比例的视频时长。 */
    val baseWeight: Float,
    /** 触发进度调节的横向阈值（像素）。 */
    private val xThreshold: Float,
    /** 纵向拉到 `min(width, height) × 该比例` 时达到最大精度。 */
    private val yMaxRangeRatio: Float = 0.5F,
    /** 最精细时的速度比例（相对基础权重）。 */
    private val minWeight: Float = 0.05F
) {

    /** 上一次采样点（横坐标），用于计算帧增量。 */
    var lastX: Float = 0F
        private set

    /** 是否已进入进度拖拽模式。 */
    var isSeeking: Boolean = false
        private set

    /** 累计时间权重（相对视频总长的比例），交给播放器换算成毫秒。 */
    var accumulatedTimeWeight: Float = 0F
        private set

    /** 纵向全长（像素）：达到它时精度最低。 */
    private var yRangeSize: Float = 100F

    /** 手势捕获：重置本次拖拽的起点状态。 */
    fun capture(viewWidth: Int, viewHeight: Int, currentX: Float) {
        lastX = currentX
        isSeeking = false
        accumulatedTimeWeight = 0F
        yRangeSize = min(viewWidth, viewHeight) * yMaxRangeRatio
    }

    /**
     * 单指移动时调用。
     *
     * @return 本帧需要应用的更新；`null` 表示「还没进入进度模式，无回调」。
     */
    fun move(
        viewWidth: Int,
        touchDownX: Float,
        touchDownY: Float,
        currentX: Float,
        currentY: Float
    ): Update? {
        val absDx = (currentX - touchDownX).absoluteValue
        val startedSeeking = !isSeeking && absDx > xThreshold
        if (startedSeeking) {
            isSeeking = true
        }
        if (!isSeeking) {
            // 仍在「纯按住」阶段：更新采样点，避免切入瞬间突跳
            lastX = currentX
            return null
        }
        val deltaX = currentX - lastX
        lastX = currentX

        val dy = (currentY - touchDownY).absoluteValue
        val ratioY = (dy / yRangeSize).coerceIn(0F, 1F)
        // 从 1.0（常速）平滑降到 minWeight（精细）
        val precision = 1F - (1F - minWeight) * ratioY
        val speed = baseWeight * precision
        accumulatedTimeWeight += (deltaX / viewWidth.toFloat()) * speed
        return Update(
            weight = accumulatedTimeWeight,
            speed = precision,
            startedSeeking = startedSeeking
        )
    }

    /** 手势释放：返回释放时是否处于进度模式（决定是收敛进度还是恢复倍速）。 */
    fun release(): Boolean {
        val wasSeeking = isSeeking
        isSeeking = false
        return wasSeeking
    }

    /** 单帧更新结果。 */
    data class Update(
        /** 累计时间权重（相对总长比例）。 */
        val weight: Float,
        /** 当前精度（1.0 = 常速，越小越精细）。 */
        val speed: Float,
        /** 本帧是否刚进入进度模式（用于触发「停止倍速 + 显示进度 UI」）。 */
        val startedSeeking: Boolean
    )
}
