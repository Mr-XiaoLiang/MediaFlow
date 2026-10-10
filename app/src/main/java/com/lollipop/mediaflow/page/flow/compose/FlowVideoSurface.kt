package com.lollipop.mediaflow.page.flow.compose

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.lollipop.mediaflow.data.local.MediaInfo
import com.lollipop.mediaflow.page.flow.compose.gesture.FlowGestureState
import com.lollipop.mediaflow.page.flow.compose.gesture.flowGestureTransform
import com.lollipop.mediaflow.playback.FlowPlaybackController
import com.lollipop.mediaflow.playback.rememberFlowPlayer
import com.lollipop.mediaflow.playback.rememberVideoAspectRatio
import com.lollipop.mediaflow.playback.toFlowMediaItem
import com.lollipop.mediaflow.ui.image.MediaImage

/**
 * 视频页的「内容区」（对应 `page_video_flow.xml` 的 `videoBackground` + `MatrixFrameLayout(PlayerView)`
 * + `artworkView`，不含控件层）。
 *
 * 分层（自下而上）：
 * 1. **模糊背景**：`MediaImage` 取极小尺寸（[BlurTargetSize]）后 `Modifier.blur`，再叠一层压暗遮罩。
 *    模糊仅在 API 31+ 生效（`Modifier.blur` 在低版本是 no-op，与旧实现用 `RenderEffect` 的门控一致）；
 * 2. **变换容器**：播放画面 + 封面遮罩同处 [flowGestureTransform] 中，因此缩放 / 平移作用于两者，
 *    滑块与 OSD 由页面放进同一容器即可共享变换（计划第四节要求）；
 * 3. **封面遮罩**：首帧渲染前用 `MediaImage` 盖住画面（首帧到达即淡出），替代旧实现的 `artworkView`。
 *
 * [onPlayerReady] 把本页的 player 交给页面（播控 / 字幕 / 进度），页面亦可忽略。
 */
@Composable
fun FlowVideoSurface(
    controller: FlowPlaybackController,
    media: MediaInfo.File,
    gesture: FlowGestureState,
    modifier: Modifier = Modifier,
    isBlurBackgroundEnabled: Boolean = true,
    onPlayerReady: (ExoPlayer?) -> Unit = {}
) {
    val mediaItem = remember(media) {
        media.toFlowMediaItem()
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isBlurBackgroundEnabled) {
            FlowBlurBackground(media = media, modifier = Modifier.fillMaxSize())
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .flowGestureTransform(gesture)
        ) {
            val player = rememberFlowPlayer(
                controller = controller,
                mediaItem = mediaItem,
                modifier = Modifier.fillMaxSize()
            )
            // 画面比例是否已就绪（`onVideoSizeChanged` 已生效）。
            // 在此之前 Surface 本身保持不可见（见 rememberFlowPlayer），封面也必须继续盖着，
            // 否则会看到一帧「按整屏比例拉伸」的画面——即打开页面时闪过的那一帧。
            val isSurfaceRatioReady = rememberVideoAspectRatio(player) > 0F
            val isFirstFrameReady = rememberFirstFrameState(player)
            LaunchedEffect(player) {
                onPlayerReady(player)
            }
            // 首帧渲染完成 **且** 比例就绪后，封面才淡出
            AnimatedVisibility(
                visible = !(isFirstFrameReady && isSurfaceRatioReady),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                MediaImage(
                    data = media.uri,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * 模糊背景：极小图 + 模糊 + 压暗遮罩。
 *
 * 外壳的侧栏过渡底图会复用本实现（同样的 `targetSize` / 模糊半径 / 压暗色）。
 * 这一点很关键：参数完全一致时两次请求是**同一个 Coil 缓存 key**，
 * 底图可直接命中页内已加载的缓存条目——既不需要重新解码，也不需要导出 Bitmap。
 */
@Composable
internal fun FlowBlurBackground(media: MediaInfo.File, modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return
    }
    Box(modifier = modifier) {
        MediaImage(
            data = media.uri,
            contentScale = ContentScale.Crop,
            targetSize = BlurTargetSize,
            modifier = Modifier
                .fillMaxSize()
                .blur(BlurRadius)
        )
        // 压暗遮罩：保证白色字幕 / 控件的可读性
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BlurScrim)
        )
    }
}

/**
 * 首帧是否已渲染。
 *
 * 用 [Player.Listener.onRenderedFirstFrame] 判定：到达后封面遮罩淡出，避免"封面停留一下"观感。
 */
@Composable
private fun rememberFirstFrameState(player: ExoPlayer?): Boolean {
    var isFirstFrameReady by remember(player) { mutableStateOf(false) }
    DisposableEffect(player) {
        if (player == null) {
            return@DisposableEffect onDispose { }
        }
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                isFirstFrameReady = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }
    LaunchedEffect(player) {
        // 换页 / 换媒体时重置，避免上一页的首帧状态被沿用
        isFirstFrameReady = false
    }
    return isFirstFrameReady
}

/** 模糊底图的目标解码尺寸：与旧实现 `override(20)` 对齐。 */
private val BlurTargetSize = IntSize(20, 20)

/** 模糊半径：与旧实现 `RenderEffect.createBlurEffect(50F, 50F, …)` 对齐。 */
private val BlurRadius = 50.dp

/** 压暗遮罩颜色（待真机与旧观感比对，见台账 M4 条目）。 */
private val BlurScrim = Color.Black.copy(alpha = 0.35F)
