package com.lollipop.mediaflow.tools

import com.lollipop.mediaflow.ui.FlowPlayerGestureHost
import kotlin.math.absoluteValue
import kotlin.math.min

class VideoTouchHelper(
    /**
     * 基础权重（屏幕横移一周代表 30% 视频长度）
     */
    private val baseWeight: Float,
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

    private var lastX = 0F
    private var isSeeking = false
    private var accumulatedTimeWeight = 0F
    private var yRangeSize = 100F

    override fun onTouchCapture(
        viewWidth: Int,
        viewHeight: Int,
        touchDownX: Float,
        touchDownY: Float,
        currentX: Float,
        currentY: Float
    ) {
        lastX = currentX
        isSeeking = false
        accumulatedTimeWeight = 0F
        videoController.startPlaybackSpeed()
        yRangeSize = min(viewWidth, viewHeight) * yMaxRangeRatio
    }

    private fun onSwitchToSeekMode() {
        videoController.stopPlaybackSpeed()
        videoController.startSeekMode()
    }

    override fun onTouchMove(
        viewWidth: Int,
        viewHeight: Int,
        touchDownX: Float,
        touchDownY: Float,
        currentX: Float,
        currentY: Float
    ) {
        // 1. 计算当前相对于起始点的绝对位移
        val absDx = (currentX - touchDownX).absoluteValue

        // 2. 状态切换判定：如果还没进入 Seeking 且横向拉开了距离
        if (!isSeeking && absDx > xThreshold) {
            isSeeking = true
            onSwitchToSeekMode() // 回调：停止倍速播放，显示进度 UI
        }

        if (isSeeking) {
            // 3. 计算这一帧的增量 deltaX
            val deltaX = currentX - lastX
            lastX = currentX // 锁定当前点，作为增量计算的起点

            // 4. 【核心】基于 Y 轴位置的丝滑权重函数 (CVT 变速逻辑)
            // 计算 Y 轴偏移占可用高度的比例 (0.0 ~ 1.0)
            val dy = (currentY - touchDownY).absoluteValue
            val ratioY = (dy / yRangeSize).coerceIn(0f, 1.0f)

            // 使用插值函数平滑精度：从 1.0 (常速) 丝滑降至 minWeight (精细)
            // 你可以根据需求换成二次函数 (ratioY * ratioY) 让感知更灵敏
            val precision = 1.0f - (1.0f - minWeight) * ratioY

            // 5. 最终毫秒增量计算
            // 增量 = (帧位移比例) * 视频总长 * 基础权重 * 当前精度
            val frameOffset = (deltaX / viewWidth.toFloat()) * baseWeight * precision

            accumulatedTimeWeight += frameOffset

            // 6. 回调给外部，驱动 UI 上的“放大镜”效果（传入 precision 供 UI 拉伸坐标轴）
            videoController.onSeek(accumulatedTimeWeight, precision)
        } else {
            // 仍在“纯倍速”模式下，更新 lastX 确保切换瞬间没有突跳
            lastX = currentX
        }
    }

    override fun onTouchRelease() {
        if (isSeeking) {
            videoController.stopSeekMode(accumulatedTimeWeight)
        } else {
            videoController.stopPlaybackSpeed()
        }
        isSeeking = false
    }

    interface VideoController {

        fun startPlaybackSpeed()

        fun stopPlaybackSpeed()

        fun startSeekMode()

        fun onSeek(weight: Float, precision: Float)

        fun stopSeekMode(weight: Float)
    }

}