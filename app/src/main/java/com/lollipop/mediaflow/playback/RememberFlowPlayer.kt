package com.lollipop.mediaflow.playback

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW

/**
 * 单页播放器（从池中取用 / 归还），并直接把画面渲染出来。
 *
 * ## 生命周期（对齐计划第三节「播放器池」）
 * 1. **取**：`acquire()` 是挂起函数；快速滑页时协程被取消，天然不会泄漏 player；
 * 2. **归还**：只有**离屏**（组合销毁）才 `yield()`；窗口内的页不归还，因此保持已 prepare
 *    并持续缓冲（加载由 `LoadControl` 的缓冲目标驱动，与是否播放无关），各自渲染自己的首帧；
 * 3. **装载**：`setMediaItem` + `prepare()`，**不自动播放**——播放态由页面决定（当前页才播）；
 * 4. **展示**：`PlayerSurface` 使用 `texture_view`，便于后续用 `graphicsLayer` 做缩放 / 位移变换。
 *
 * 返回的 player 供页面做播控（播放 / 暂停 / 倍速 / 轨道 / 进度），页面也可忽略。
 *
 * @param mediaItem 本页要播放的媒体；为 null 时只取 player 不装载（例如数据尚未就绪）。
 */
@OptIn(UnstableApi::class)
@Composable
fun rememberFlowPlayer(
    controller: FlowPlaybackController,
    mediaItem: MediaItem?,
    modifier: Modifier = Modifier
): ExoPlayer? {
    var player by remember(controller) { mutableStateOf<ExoPlayer?>(null) }

    LaunchedEffect(controller) {
        player = controller.playerPool.acquire()
    }

    DisposableEffect(controller) {
        onDispose {
            // 读取的是最新 state 值：acquire 完成后才能拿到 player
            player?.let { controller.playerPool.yield(it) }
            player = null
        }
    }

    LaunchedEffect(player, mediaItem) {
        val exoPlayer = player ?: return@LaunchedEffect
        if (mediaItem == null) {
            return@LaunchedEffect
        }
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
    }

    val exoPlayer = player
    if (exoPlayer != null) {
        PlayerSurface(
            player = exoPlayer,
            modifier = modifier,
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW
        )
    }
    return exoPlayer
}

/**
 * 页面级预缓存调度：跟随当前索引维护外圈 ±2 的 [androidx.media3.exoplayer.source.preload.PreCacheHelper]。
 *
 * 用**当前页索引**调用（Pager 宿主调用一次），不要放进每一页——窗口是相对「当前项」定义的。
 */
@Composable
fun FlowPreloadEffect(
    controller: FlowPlaybackController,
    currentIndex: Int,
    items: List<MediaItem>
) {
    LaunchedEffect(controller, currentIndex, items) {
        controller.preload.setCurrentIndex(currentIndex, items)
    }
}
