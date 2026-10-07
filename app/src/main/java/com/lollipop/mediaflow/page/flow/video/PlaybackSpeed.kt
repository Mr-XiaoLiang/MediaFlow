package com.lollipop.mediaflow.page.flow.video

import android.graphics.Color
import android.view.Gravity
import android.view.View
import androidx.media3.common.PlaybackParameters
import com.lollipop.common.ui.view.IconPopupMenu
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.mediaflow.video.VideoController

class PlaybackSpeed(
    private val onSpeedChanged: (Int, String) -> Unit
) {

    private val speedPopupHolder by lazy {
        IconPopupMenu.hold(::buildSpeedPopup)
    }

    fun showChoosePopup(anchor: View) {
        speedPopupHolder.show(anchor)
    }

    private var enableColor = Color.WHITE
    private var disableColor = Color.GRAY

    private var isEnable = false
    private var speedValue = ""

    fun init(enableColor: Int, disableColor: Int) {
        isEnable = false
        this.enableColor = enableColor
        this.disableColor = disableColor
        onSpeedChanged(getSpeed())
    }

    fun getSpeed(): Float {
        return Preferences.playbackSpeed.get()
    }

    fun toggleSpeed(controller: VideoController) {
        val currentSpeed = controller.getPlaybackSpeed()
        val speed = getSpeed()
        // 放大100倍之后取整数，以此来严格对比速度是否是1.00倍
        // 如果是，表示我们当前没有开启倍速，我们就开启它，设置为我们设置的倍速
        if ((currentSpeed * 100).toInt() == 100) {
            controller.setPlaybackSpeed(speed)
        } else {
            // 否则，设置为1.0倍速
            controller.setPlaybackSpeed(1.0F)
        }
    }

    private fun onChoose(speed: Float) {
        Preferences.playbackSpeed.set(speed)
        onSpeedChanged(speed)
    }

    private fun onSpeedChanged(speed: Float) {
        speedValue = speedDisplay(speed)
        dispatchSpeedChanged()
    }

    fun onSpeedChanged(playbackParameters: PlaybackParameters) {
        isEnable = (playbackParameters.speed * 100).toInt() != 100
        if (isEnable) {
            onSpeedChanged(playbackParameters.speed)
        } else {
            onSpeedChanged(getSpeed())
        }
    }

    private fun dispatchSpeedChanged() {
        onSpeedChanged(
            if (isEnable) {
                enableColor
            } else {
                disableColor
            }, speedValue
        )
    }

    private fun speedDisplay(speed: Float): String {
        if (speed < 1) {
            val value = (speed * 100).toInt()
            if (value % 10 == 0) {
                return ".${value / 10}X"
            }
            return ".${value}X"
        } else {
            val first = speed.toInt()
            val second = ((speed - first) * 100).toInt()
            if (second == 0) {
                return "${first}X"
            } else if (second % 10 == 0) {
                return "${first}.${second / 10}X"
            }
            return "${first}.${second}X"
        }
    }

    private fun buildSpeedPopup(builder: IconPopupMenu.Builder) {
        VideoSpeed.entries.forEach { item ->
            builder.addMenu(
                tag = item.tag,
                titleRes = item.label,
                iconRes = 0
            )
        }
        builder
            .gravity(Gravity.START or Gravity.TOP)
            .offsetDp(8, -8)
            .onClick { item ->
                val newMode = VideoSpeed.findByTag(item.tag) ?: VideoSpeed.ONE
                onChoose(newMode.speed)
                true
            }
    }

    private enum class VideoSpeed(val tag: String, val speed: Float, val label: Int) {
        ZERO_POINT_TWO(tag = "0.25", speed = 0.25F, label = R.string.video_speed_0_25),
        ZERO_POINT_FIVE(tag = "0.50", speed = 0.5F, label = R.string.video_speed_0_5),
        ZERO_POINT_SEVEN(tag = "0.75", speed = 0.75F, label = R.string.video_speed_0_75),
        ONE(tag = "1.0", speed = 1.0F, label = R.string.video_speed_1_0),
        ONE_POINT_TWO(tag = "1.25", speed = 1.25F, label = R.string.video_speed_1_25),
        ONE_POINT_FIVE(tag = "1.5", speed = 1.5F, label = R.string.video_speed_1_5),
        ONE_POINT_SEVEN(tag = "1.75", speed = 1.75F, label = R.string.video_speed_1_75),
        TWO(tag = "2.0", speed = 2.0F, label = R.string.video_speed_2_0),
        TWO_POINT_FIVE(tag = "2.5", speed = 2.5F, label = R.string.video_speed_2_5),
        THREE(tag = "3.0", speed = 3.0F, label = R.string.video_speed_3_0),
        FOUR(tag = "4.0", speed = 4.0F, label = R.string.video_speed_4_0);

        companion object {
            fun findByTag(tag: String): VideoSpeed? {
                return VideoSpeed.entries.find { it.tag == tag }
            }
        }
    }
}