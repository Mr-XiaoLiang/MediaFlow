package com.lollipop.mediaflow.page.flow.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.media3.exoplayer.ExoPlayer
import com.lollipop.mediaflow.data.local.ArchiveManager
import com.lollipop.mediaflow.data.local.ArchiveQuick
import com.lollipop.mediaflow.data.local.MediaInfo
import com.lollipop.mediaflow.page.flow.compose.control.FlowSliderTouchMode
import com.lollipop.mediaflow.page.flow.compose.gesture.FlowGestureState
import com.lollipop.mediaflow.page.flow.compose.gesture.flowGesture
import com.lollipop.mediaflow.page.flow.compose.gesture.rememberFlowClickCounter
import com.lollipop.mediaflow.page.flow.compose.gesture.rememberFlowGestureState
import com.lollipop.mediaflow.playback.FlowPlaybackController
import com.lollipop.mediaflow.playback.FlowPreloadEffect
import com.lollipop.mediaflow.playback.gesture.FlowSeekGestureListener
import com.lollipop.mediaflow.playback.toFlowMediaItem
import com.lollipop.mediaflow.tools.DisplayFormater
import com.lollipop.mediaflow.tools.Preferences
import kotlinx.coroutines.delay

/**
 * 视频页（纯 Compose）：`VerticalPager` + 每页的「内容区 / 手势 / 控件层 / 字幕」。
 *
 * 与旧实现的分工对照：
 * - `ViewPager2 + PlayAdapter + VideoPlayHolder` → [VerticalPager] + [FlowVideoPageItem]；
 * - 手势宿主 `FlowPlayerGestureHost` + `VideoTouchHelper` → [`flowGesture`] + [FlowSeekGestureListener]
 *   （换算仍用同一份 `TouchSeekMath`）；
 * - `ClickHelper` 的 1/2/3 击 → [rememberFlowClickCounter]（由手势源的轻触上报驱动）；
 * - 「播放器窗口 ±1」由 [VerticalPager] 的 `beyondViewportPageCount = 1` 保证：
 *   相邻页各自 acquire 到已 prepare 的 player，切页无"上一帧停留"空窗。
 *
 * 数据由调用方传入（里程碑 5 负责观察 `MediaView` 与归档 / 位置保存的业务）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoFlowComposeScreen(
    controller: FlowPlaybackController,
    items: List<MediaInfo.File>,
    initialIndex: Int = 0,
    /** 装饰层是否显示（控件层随它显隐；字幕层独立显示）。 */
    isDecorationVisible: Boolean = true,
    /** 当前倍速标签（如 `2.0X`；空串表示不显示倍速按钮）。 */
    quickSpeedLabel: String = "",
    /** 是否允许归档（旧 `archiveEnable`）。 */
    isArchiveEnabled: Boolean = true,
    onIndexChanged: (Int) -> Unit = {},
    onChangeDecoration: (Boolean) -> Unit = {},
    onArchiveClick: (MediaInfo.File, ArchiveQuick) -> Unit = { _, _ -> },
    onSubtitleClick: (MediaInfo.File, ExoPlayer?) -> Unit = { _, _ -> }
) {
    if (items.isEmpty()) {
        return
    }
    val pagerState = rememberPagerState(initialPage = initialIndex.coerceIn(items.indices)) {
        items.size
    }
    val mediaItems = remember(items) {
        items.map { media -> media.toFlowMediaItem() }
    }
    // 外圈 ±2 预缓存（页面级，只调用一次）
    FlowPreloadEffect(
        controller = controller,
        currentIndex = pagerState.currentPage,
        items = mediaItems
    )
    LaunchedEffect(pagerState.currentPage) {
        onIndexChanged(pagerState.currentPage)
    }
    VerticalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        // 与播放器池的保活 ±1 对齐
        beyondViewportPageCount = 1
    ) { page ->
        FlowVideoPageItem(
            controller = controller,
            media = items[page],
            isCurrent = page == pagerState.currentPage,
            isControlVisible = isDecorationVisible,
            quickSpeedLabel = quickSpeedLabel,
            isArchiveEnabled = isArchiveEnabled,
            onChangeDecoration = onChangeDecoration,
            onArchiveClick = onArchiveClick,
            onSubtitleClick = onSubtitleClick
        )
    }
}

/** 单页：内容区 + 手势 + 控件层 + 字幕层。 */
@Composable
private fun FlowVideoPageItem(
    controller: FlowPlaybackController,
    media: MediaInfo.File,
    isCurrent: Boolean,
    isControlVisible: Boolean,
    quickSpeedLabel: String,
    isArchiveEnabled: Boolean,
    onChangeDecoration: (Boolean) -> Unit,
    onArchiveClick: (MediaInfo.File, ArchiveQuick) -> Unit,
    onSubtitleClick: (MediaInfo.File, ExoPlayer?) -> Unit
) {
    val gesture = rememberFlowGestureState()
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var speedLabel by remember(quickSpeedLabel) { mutableStateOf(quickSpeedLabel) }
    // 触摸进度模式
    var isTouchSeek by remember { mutableStateOf(false) }
    var seekStartMs by remember { mutableLongStateOf(0L) }
    var osdPrecision by remember { mutableFloatStateOf(1F) }
    // 滑块拖动
    var isSliding by remember { mutableStateOf(false) }
    var slideProgress by remember { mutableFloatStateOf(0F) }

    val clickCounter = rememberFlowClickCounter { count ->
        when (count) {
            1 -> onChangeDecoration(!isControlVisible)
            2 -> {
                onChangeDecoration(true)
                player?.let { exo ->
                    if (exo.isPlaying) {
                        exo.pause()
                    } else {
                        exo.play()
                    }
                }
            }

            else -> gesture.reset()
        }
    }

    val xThreshold = LocalViewConfiguration.current.touchSlop * 2F
    val seekListener = remember(xThreshold) {
        FlowSeekGestureListener(
            baseWeight = Preferences.videoTouchSeekBaseWeight.get(),
            xThreshold = xThreshold,
            onStartSpeed = {
                player?.setPlaybackSpeed(Preferences.playbackSpeed.get())
            },
            onStopSpeed = {
                player?.setPlaybackSpeed(1F)
            },
            onStartSeek = {
                val exo = player ?: return@FlowSeekGestureListener
                seekStartMs = exo.currentPosition
                osdPrecision = 1F
                isTouchSeek = true
                exo.pause()
            },
            onSeek = { weight, speed ->
                val exo = player ?: return@FlowSeekGestureListener
                osdPrecision = speed
                if (durationMs > 0L) {
                    exo.seekTo((seekStartMs + weight * durationMs).toLong().coerceIn(0L, durationMs))
                }
            },
            onStopSeek = { weight ->
                val exo = player ?: return@FlowSeekGestureListener
                if (durationMs > 0L) {
                    exo.seekTo((seekStartMs + weight * durationMs).toLong().coerceIn(0L, durationMs))
                }
                isTouchSeek = false
                exo.play()
            },
            onTapAction = { clickCounter.click() }
        )
    }

    // 进度轮询：仅当前页，避免后台页空转
    LaunchedEffect(player, isCurrent) {
        val exo = player ?: return@LaunchedEffect
        if (!isCurrent) {
            return@LaunchedEffect
        }
        while (true) {
            durationMs = exo.duration.coerceAtLeast(0L)
            if (!isSliding && !isTouchSeek) {
                positionMs = exo.currentPosition.coerceAtLeast(0L)
            }
            isPlaying = exo.isPlaying
            delay(ProgressPollIntervalMs)
        }
    }

    // 只有当前页播放；其余页保持已 prepare 的缓冲状态
    LaunchedEffect(player, isCurrent) {
        val exo = player ?: return@LaunchedEffect
        if (isCurrent) {
            exo.play()
        } else {
            exo.pause()
        }
    }

    val progress = when {
        isSliding -> slideProgress
        isTouchSeek && durationMs > 0L -> (positionMs.toFloat() / durationMs).coerceIn(0F, 1F)
        durationMs > 0L -> (positionMs.toFloat() / durationMs).coerceIn(0F, 1F)
        else -> 0F
    }
    val archiveActions = remember(isArchiveEnabled) {
        if (isArchiveEnabled) {
            ArchiveQuick.entries.filter { ArchiveManager.isQuickEnable(it) }
        } else {
            emptyList()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .flowGesture(
                state = gesture,
                listener = seekListener,
                enabled = true,
                penetrateBounds = { emptyList() }
            )
    ) {
        FlowVideoSurface(
            controller = controller,
            media = media,
            gesture = gesture,
            isBlurBackgroundEnabled = Preferences.isBlurVideoBackground.get(),
            onPlayerReady = { exo -> player = exo }
        )

        // 字幕独立于控件显隐
        FlowSubtitleOverlay(
            player = player,
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(visible = isControlVisible) {
            FlowVideoControlLayer(
                state = FlowVideoControlState(
                    progress = progress,
                    progressText = progressText(positionMs, durationMs),
                    showProgressText = Preferences.isShowVideoProgressText.get(),
                    showPlayButton = !isPlaying,
                    quickSpeedLabel = speedLabel,
                    showQuickSpeed = speedLabel.isNotEmpty(),
                    archiveActions = archiveActions,
                    rewindLabel = if (isTouchSeek) "-${seekOffsetLabel(seekStartMs, positionMs)}" else "",
                    forwardLabel = if (isTouchSeek) "+${seekOffsetLabel(seekStartMs, positionMs)}" else "",
                    osd = if (isTouchSeek) {
                        FlowOsdState(
                            totalDuration = durationMs,
                            progressMs = positionMs,
                            baseWeight = Preferences.videoTouchSeekBaseWeight.get(),
                            precision = osdPrecision
                        )
                    } else {
                        null
                    }
                ),
                modifier = Modifier.fillMaxSize(),
                onSeekProgress = { value ->
                    slideProgress = value
                    if (durationMs > 0L) {
                        positionMs = (value * durationMs).toLong()
                    }
                },
                onSeekTouchDown = {
                    isSliding = true
                    slideProgress = progress
                    clickCounter.reset()
                },
                onSeekTouchUp = {
                    isSliding = false
                    if (durationMs > 0L) {
                        player?.seekTo((slideProgress * durationMs).toLong().coerceIn(0L, durationMs))
                    }
                },
                onPlayClick = {
                    player?.let { exo ->
                        if (exo.isPlaying) {
                            exo.pause()
                        } else {
                            exo.play()
                        }
                    }
                },
                onQuickSpeedClick = {
                    val exo = player ?: return@FlowVideoControlLayer
                    val target = if (exo.playbackParameters.speed == 1F) {
                        Preferences.playbackSpeed.get()
                    } else {
                        1F
                    }
                    exo.setPlaybackSpeed(target)
                    speedLabel = speedLabelOf(target)
                },
                onQuickSpeedLongClick = {
                    // 长按选择倍速弹窗：里程碑 4 后续接入 ComposePopupMenu（见台账 M4-7）
                },
                onSubtitleClick = { onSubtitleClick(media, player) },
                onArchiveClick = { quick -> onArchiveClick(media, quick) },
                sliderTouchMode = if (Preferences.isVideoSliderTapEnable.get()) {
                    FlowSliderTouchMode.Tap
                } else {
                    FlowSliderTouchMode.Drag
                }
            )
        }
    }
}

private fun progressText(positionMs: Long, durationMs: Long): String {
    return "${DisplayFormater.formatTime(positionMs)} / ${DisplayFormater.formatTime(durationMs)}"
}

private fun seekOffsetLabel(startMs: Long, currentMs: Long): String {
    val seconds = ((currentMs - startMs) / 1000L).let { kotlin.math.abs(it) }
    return "${seconds}S"
}

private fun speedLabelOf(speed: Float): String {
    return if (speed == speed.toInt().toFloat()) {
        "${speed.toInt()}.0X"
    } else {
        "${speed}X"
    }
}

/** 进度轮询间隔。 */
private const val ProgressPollIntervalMs = 200L
