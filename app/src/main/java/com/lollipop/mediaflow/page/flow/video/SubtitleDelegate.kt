package com.lollipop.mediaflow.page.flow.video

import android.graphics.Color
import android.graphics.Typeface
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView

object SubtitleDelegate {

    fun postUpdateSubtitle(playerView: PlayerView) {
        playerView.post {
            updateSubtitle(playerView)
        }
    }

    @OptIn(UnstableApi::class)
    fun updateSubtitle(playerView: PlayerView) {
        // 在初始化 PlayerView 时设置
        playerView.subtitleView?.let {
            it.setViewType(SubtitleView.VIEW_TYPE_CANVAS)
            it.setStyle(
                CaptionStyleCompat(
                    // 字体颜色
                    Color.WHITE,
                    // 背景颜色（设为透明更现代）
                    Color.TRANSPARENT,
                    // 窗口颜色
                    Color.TRANSPARENT,
                    // 边缘效果：外阴影
                    CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                    // 阴影颜色
                    Color.BLACK,
                    // 字体样式
                    Typeface.DEFAULT
                )
            )
            // 设置字幕大小（比例单位）
            val playerWidth = it.width
            val playerHeight = it.height
            val subtitleWeight = if (playerWidth > playerHeight) {
                1F
            } else {
                0.6F
            }
            it.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subtitleWeight)
        }
    }

}