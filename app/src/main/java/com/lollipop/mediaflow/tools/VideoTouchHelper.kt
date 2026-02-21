package com.lollipop.mediaflow.tools

import androidx.media3.common.PlaybackParameters

class VideoTouchHelper {

    companion object {

        private const val TOUCH_WEIGHT = 2F

        val DEFAULT_SPEED by lazy {
            PlaybackParameters(1F)
        }

        fun playSpeed(viewWidth: Int, currentX: Int, touchDownX: Int): PlaybackParameters {
            // 假设屏幕宽度的 1/2 对应倍率翻倍/减半
            val touchWeight = viewWidth / TOUCH_WEIGHT
            val startSpeed = 2.0f // 长按初始速度

            // 在 onScroll 中计算
            val distanceX = currentX - touchDownX
            // 对数计算：这样向右滑是翻倍，向左滑是减半，手感极其丝滑
            var targetSpeed =
                startSpeed * Math.pow(2.0, (distanceX / touchWeight).toDouble()).toFloat()

            // 限制边界
            targetSpeed = targetSpeed.coerceIn(0.25f, 8.0f)
            return PlaybackParameters(targetSpeed)
        }

    }

}