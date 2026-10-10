package com.lollipop.mediaflow.playback

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
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
    modifier: Modifier = Modifier,
    /** 画面透明度：由调用方驱动淡入淡出（例如侧栏过渡期间暂时隐藏画面）。 */
    surfaceAlpha: Float = 1F
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
        // 画面按视频宽高比「等比放大填充」（长边撑满、短边露出底色）：
        // media3-ui-compose 的 PlayerSurface **没有 resizeMode**，必须自行约束尺寸，
        // 否则 Surface 铺满容器会导致画面被拉伸（旧 View 版由 `resize_mode="fit"` 保证）。
        val ratio = rememberVideoAspectRatio(exoPlayer)
        val isRatioKnown = ratio > 0F
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            PlayerSurface(
                player = exoPlayer,
                modifier = Modifier
                    .then(
                        if (isRatioKnown) Modifier.aspectRatio(ratio) else Modifier.fillMaxSize()
                    )
                    // 比例未知时**必须不可见**：ExoPlayer 可能先于 `onVideoSizeChanged` 的
                    // 重组就把首帧画到 Surface 上，而 Surface 会「填满自身尺寸」，
                    // 于是先以整屏比例闪出一帧被拉伸的画面（即「平铺填充到空隙」的那一帧）。
                    // TextureView 走常规合成，alpha 对它有效。
                    // 比例未知 → 强制不可见（见台账 M11-17）；比例就绪后交由调用方的 surfaceAlpha 控制
                    .alpha(if (isRatioKnown) surfaceAlpha else 0F),
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW
            )
        }
    }
    return exoPlayer
}

/**
 * 视频的显示宽高比（含旋转与像素宽高比修正）。
 *
 * 与 `PlayerView.resize_mode = fit` 同源：取 `width * pixelWidthHeightRatio / height`，
 * 并在 `unappliedRotationDegrees` 为 90 / 270 时交换宽高。
 *
 * **返回 0 表示尺寸未知**——调用方在此阶段必须让画面保持不可见（见 [rememberFlowPlayer]），
 * 否则会闪出一帧按错误比例渲染的画面。
 *
 * 该状态是幂等的（只读 [ExoPlayer.videoSize] 并监听变化），因此页面层可以再次调用它，
 * 用「比例是否就绪」来决定封面遮罩何时才允许淡出。
 */
@Composable
fun rememberVideoAspectRatio(player: ExoPlayer?): Float {
    var ratio by remember(player) { mutableFloatStateOf(0F) }
    DisposableEffect(player) {
        if (player == null) {
            return@DisposableEffect onDispose { }
        }
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                ratio = videoSize.displayAspectRatio()
            }
        }
        player.addListener(listener)
        // 补一次初值：尺寸可能在本组合进入前就已确定（例如复用的池内 player）
        ratio = player.videoSize.displayAspectRatio()
        onDispose { player.removeListener(listener) }
    }
    return ratio
}

/** 计算 [VideoSize] 的显示宽高比；尺寸无效时返回 0。 */
private fun VideoSize.displayAspectRatio(): Float {
    if (width <= 0 || height <= 0) {
        return 0F
    }
    val isRotated = unappliedRotationDegrees % 180 != 0
    val displayWidth = if (isRotated) height else width
    val displayHeight = if (isRotated) width else height
    val ratio = displayWidth * pixelWidthHeightRatio / displayHeight
    return if (ratio > 0F && ratio.isFinite()) ratio else 0F
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
