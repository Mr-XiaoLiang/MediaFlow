package com.lollipop.mediaflow.page.flow.compose.control

import androidx.annotation.StringRes
import com.lollipop.mediaflow.R

/**
 * 倍速候选（逐项对齐旧 `PlaybackSpeed.VideoSpeed`）。
 *
 * 显示格式同样复刻旧实现的 `speedDisplay`：小于 1 倍省略整数位（`.5X`），
 * 大于 1 倍省略末尾的 0（`2X` / `2.5X`）。
 */
enum class FlowPlaybackSpeed(val speed: Float, @StringRes val label: Int) {
    X025(speed = 0.25F, label = R.string.video_speed_0_25),
    X050(speed = 0.5F, label = R.string.video_speed_0_5),
    X075(speed = 0.75F, label = R.string.video_speed_0_75),
    X100(speed = 1.0F, label = R.string.video_speed_1_0),
    X125(speed = 1.25F, label = R.string.video_speed_1_25),
    X150(speed = 1.5F, label = R.string.video_speed_1_5),
    X175(speed = 1.75F, label = R.string.video_speed_1_75),
    X200(speed = 2.0F, label = R.string.video_speed_2_0),
    X250(speed = 2.5F, label = R.string.video_speed_2_5),
    X300(speed = 3.0F, label = R.string.video_speed_3_0),
    X400(speed = 4.0F, label = R.string.video_speed_4_0);

    companion object {

        /** 是否等同于 1 倍速（严格比较，避免浮点误差）。 */
        fun isNormalSpeed(speed: Float): Boolean {
            return (speed * 100).toInt() == 100
        }

        /** 倍速标签文本。 */
        fun display(speed: Float): String {
            if (speed < 1F) {
                val value = (speed * 100).toInt()
                return if (value % 10 == 0) {
                    ".${value / 10}X"
                } else {
                    ".${value}X"
                }
            }
            val first = speed.toInt()
            val second = ((speed - first) * 100).toInt()
            return when {
                second == 0 -> "${first}X"
                second % 10 == 0 -> "${first}.${second / 10}X"
                else -> "${first}.${second}X"
            }
        }
    }
}
