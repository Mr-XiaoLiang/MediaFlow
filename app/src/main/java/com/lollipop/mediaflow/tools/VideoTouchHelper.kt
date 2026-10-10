package com.lollipop.mediaflow.tools

import android.graphics.Matrix
import com.lollipop.common.ui.view.FlowPlayerGestureHost
import com.lollipop.mediaflow.playback.gesture.TouchSeekMath

/**
 * 进度手势适配器（**View 手势源**）。
 *
 * 换算逻辑已抽到 [TouchSeekMath]（与事件来源无关）：本类只做两件事——
 * 1. 把 [`FlowPlayerGestureHost.OnFlowTouchListener`] 的回调转发给 [TouchSeekMath]；
 * 2. 把数学结果翻译成 [VideoController] 的业务调用。
 *
 * Compose 手势源（新外壳）直接复用 [TouchSeekMath]，不再复制算法。
 */
class VideoTouchHelper(
    /**
     * 基础权重（屏幕横移一周代表 30% 视频长度）
     */
    val baseWeight: Float,
    /**
     * 触发进度调节的横向阈值
     */
    private val xThreshold: Float,
    /**
     * Y轴拉动到 1/2 屏幕高度时达到最大精度
     */
    private val yMaxRangeRatio: Float = 0.5F,
    /**
     * 最精细时，速度降为 5%
     */
    private val minWeight: Float = 0.05F,
    private val videoController: VideoController
) : FlowPlayerGestureHost.OnFlowTouchListener {

    private val math = TouchSeekMath(
        baseWeight = baseWeight,
        xThreshold = xThreshold,
        yMaxRangeRatio = yMaxRangeRatio,
        minWeight = minWeight
    )

    /** 上一次采样点（横坐标），转发 [TouchSeekMath.lastX]。 */
    val lastX: Float
        get() {
            return math.lastX
        }

    override fun onSingleCapture(
        viewWidth: Int,
        viewHeight: Int,
        touchDownX: Float,
        touchDownY: Float,
        currentX: Float,
        currentY: Float
    ) {
        math.capture(
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            currentX = currentX
        )
        videoController.startTouchPlaybackSpeed()
    }

    private fun onSwitchToSeekMode() {
        videoController.stopTouchPlaybackSpeed()
        videoController.startTouchSeekMode()
    }

    override fun onSingleMove(
        viewWidth: Int,
        viewHeight: Int,
        touchDownX: Float,
        touchDownY: Float,
        currentX: Float,
        currentY: Float
    ) {
        val update = math.move(
            viewWidth = viewWidth,
            touchDownX = touchDownX,
            touchDownY = touchDownY,
            currentX = currentX,
            currentY = currentY
        ) ?: return
        if (update.startedSeeking) {
            onSwitchToSeekMode()
        }
        videoController.onTouchSeek(update.weight, speed = update.speed)
    }

    override fun onDoubleScale(matrix: Matrix) {
        videoController.onScaleGestureChanged(matrix)
    }

    override fun onTouchRelease() {
        if (math.release()) {
            videoController.stopTouchSeekMode(math.accumulatedTimeWeight)
        } else {
            videoController.stopTouchPlaybackSpeed()
        }
    }

    interface VideoController {

        fun startTouchPlaybackSpeed()

        fun stopTouchPlaybackSpeed()

        fun startTouchSeekMode()

        fun onTouchSeek(weight: Float, speed: Float)

        fun stopTouchSeekMode(weight: Float)

        fun onScaleGestureChanged(matrix: Matrix)
    }

}
