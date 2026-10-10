package com.lollipop.mediaflow.page.flow.compose

import android.graphics.Color
import android.graphics.Typeface
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView

/**
 * 字幕层：`AndroidView` 桥接 [SubtitleView]（计划第五节的做法：不自绘）。
 *
 * 与旧实现（`SubtitleDelegate` 作用于 `PlayerView.subtitleView`）的差异：
 * - 这里是一个**独立浮层**，挂在 Compose 页面中（不再依赖 `PlayerView`）；
 * - 样式参数与旧实现逐项一致：[SubtitleView.VIEW_TYPE_CANVAS]、白字 + 透明底 + 透明窗口 +
 *   `EDGE_TYPE_DROP_SHADOW`（黑色阴影）、字号按宽高比取 `1F`（横屏）或 `0.6F`（竖屏）；
 * - cues 通过 [Player.Listener.onCues] 推送，并在切换 player 时立即同步一次 `currentCues`，
 *   避免切页瞬间字幕空窗。
 *
 * 说明：Media3 目前没有官方 Compose 字幕组件（已核对 `media3-ui-compose` 1.11.1 源码包清单），
 * 若后续官方提供则替换本层。
 */
@OptIn(UnstableApi::class)
@Composable
fun FlowSubtitleOverlay(
    player: Player?,
    modifier: Modifier = Modifier
) {
    var subtitleView by remember { mutableStateOf<SubtitleView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            SubtitleView(context).apply {
                setViewType(SubtitleView.VIEW_TYPE_CANVAS)
                // 首次布局后再计算字号比例（需要宽高）
                post { applyFlowSubtitleStyle(this) }
            }.also { subtitleView = it }
        },
        update = { view ->
            subtitleView = view
            view.post { applyFlowSubtitleStyle(view) }
        }
    )

    DisposableEffect(player, subtitleView) {
        val view = subtitleView
        if (player == null || view == null) {
            return@DisposableEffect onDispose { }
        }
        val listener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                view.setCues(cueGroup.cues)
            }
        }
        player.addListener(listener)
        // 立即同步当前 cues：切页 / 重绑定时不留空窗
        view.setCues(player.currentCues.cues)
        onDispose {
            player.removeListener(listener)
        }
    }
}

/**
 * 应用字幕样式（逐项对齐 `SubtitleDelegate.updateSubtitle`）。
 *
 * 字号：横屏用 `DEFAULT_TEXT_SIZE_FRACTION`，竖屏缩到 0.6 倍。
 */
@OptIn(UnstableApi::class)
private fun applyFlowSubtitleStyle(view: SubtitleView) {
    view.setStyle(
        CaptionStyleCompat(
            /* foregroundColor= */ Color.WHITE,
            /* backgroundColor= */ Color.TRANSPARENT,
            /* windowColor= */ Color.TRANSPARENT,
            /* edgeType= */ CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
            /* edgeColor= */ Color.BLACK,
            /* typeface= */ Typeface.DEFAULT
        )
    )
    val width = view.width
    val height = view.height
    if (width <= 0 || height <= 0) {
        return
    }
    val weight = if (width > height) 1F else 0.6F
    view.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * weight)
}
