package com.lollipop.mediaflow.page.flow.compose

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.lollipop.mediaflow.data.local.ArchiveManager
import com.lollipop.mediaflow.data.local.ArchiveQuick
import com.lollipop.mediaflow.data.LMedia
import com.lollipop.mediaflow.page.flow.compose.control.FlowChoiceDialog
import com.lollipop.mediaflow.page.flow.compose.control.FlowChoiceItem
import com.lollipop.mediaflow.page.flow.compose.control.FlowPlaybackSpeed
import com.lollipop.mediaflow.page.flow.compose.control.FlowSliderTouchMode
import com.lollipop.mediaflow.page.flow.compose.gesture.flowGesture
import com.lollipop.mediaflow.page.flow.compose.gesture.rememberFlowClickCounter
import com.lollipop.mediaflow.page.flow.compose.gesture.rememberFlowGestureState
import com.lollipop.mediaflow.playback.FlowHotKeyTarget
import com.lollipop.mediaflow.playback.FlowPipActions
import com.lollipop.mediaflow.playback.FlowPlaybackController
import com.lollipop.mediaflow.playback.FlowPreloadEffect
import com.lollipop.mediaflow.playback.applySubtitleSelection
import com.lollipop.mediaflow.playback.findSubtitleTracks
import com.lollipop.mediaflow.playback.PlaybackProgressStore
import com.lollipop.mediaflow.playback.gesture.FlowSeekGestureListener
import com.lollipop.mediaflow.playback.toFlowMediaItem
import com.lollipop.mediaflow.playback.updatePlaybackProgress
import com.lollipop.mediaflow.tools.DisplayFormater
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.mediaflow.video.VideoTrack
import com.lollipop.mediaflow.video.VideoTrackGroup
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 视频页（纯 Compose）：`VerticalPager` + 每页的「内容区 / 手势 / 控件层 / 字幕 / 弹窗」。
 *
 * 与旧实现的对照：
 * - `ViewPager2 + PlayAdapter + VideoPlayHolder` → [VerticalPager] + [FlowVideoPageItem]；
 * - `FlowPlayerGestureHost` + `VideoTouchHelper` → `flowGesture` + [FlowSeekGestureListener]；
 * - `ClickHelper` 的 1/2/3 击 → `rememberFlowClickCounter`（由手势源的轻触上报驱动）；
 * - `SubtitleSelectDialog` / `PlaybackSpeed.showChoosePopup` → [FlowChoiceDialog]（两处共用）；
 * - 字幕轨道语义 → [applySubtitleSelection]；PiP 动作与热键分别通过 [pipActions] / [hotKeyTarget]
 *   暴露给 Activity（旧实现由 `PIPHelper` + `VideoHotKeyDelegate` 直接操作 `VideoManager`）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoFlowComposeScreen(
    controller: FlowPlaybackController,
    items: List<LMedia>,
    initialIndex: Int = 0,
    isDecorationVisible: Boolean = true,
    quickSpeedLabel: String = "",
    isArchiveEnabled: Boolean = true,
    /** 是否处于画中画模式：为 true 时隐藏控件层（对齐旧 `controllerVisibleFilter.onPipChanged`）。 */
    isPipMode: Boolean = false,
    /**
     * 外壳侧栏过渡期间是否抑制内容（**阶段信号**）。
     *
     * 内容区的淡出 / 淡入与让位由外壳负责（`FlowScaffold` 的内容区容器），本页只用它做
     * 两件只有页面才知道的事：暂停播放、以及把当前项的模糊图交给 `FlowBackground`。
     */
    isContentSuppressed: Boolean = false,
    /** PiP 动作 / 状态中转（Activity 持有）。 */
    pipActions: FlowPipActions = remember { FlowPipActions() },
    /** 热键操作目标（Activity 持有；页面负责绑定「当前页 player + 翻页动作」）。 */
    hotKeyTarget: FlowHotKeyTarget = remember { FlowHotKeyTarget() },
    /** 侧栏点击后的跳转请求（`>= 0` 时生效，消费后回调 [onScrollRequestHandled]）。 */
    scrollToIndex: Int = NoScrollRequest,
    onScrollRequestHandled: () -> Unit = {},
    onIndexChanged: (Int) -> Unit = {},
    onChangeDecoration: (Boolean) -> Unit = {},
    onArchiveClick: (LMedia, ArchiveQuick) -> Unit = { _, _ -> }
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
    // 当前页 player 的登记表（页内写入，供 PiP / 热键按「当前项」取用）
    val pagePlayers = remember { mutableStateMapOf<Int, ExoPlayer?>() }
    val scope = rememberCoroutineScope()

    // 侧栏过渡：抑制期间暂停播放（对齐「点击 → 视频暂停」这一步），
    // 过渡结束后只在「过渡前本来在播」时才恢复，避免把用户的手动暂停也一并解除
    var wasPlayingBeforeTransition by remember { mutableStateOf(false) }
    LaunchedEffect(isContentSuppressed) {
        val exo = pagePlayers[pagerState.currentPage] ?: return@LaunchedEffect
        if (isContentSuppressed) {
            wasPlayingBeforeTransition = exo.isPlaying
            exo.pause()
        } else if (wasPlayingBeforeTransition) {
            exo.play()
        }
    }

    fun goToPage(target: Int) {
        scope.launch {
            pagerState.animateScrollToPage(target.coerceIn(0, items.lastIndex))
        }
    }

    // 每次重组刷新回调与状态：PiP 广播在组合作用域之外，必须取到最新的当前页
    SideEffect {
        pipActions.onPlay = { pagePlayers[pagerState.currentPage]?.play() }
        pipActions.onPause = { pagePlayers[pagerState.currentPage]?.pause() }
        pipActions.onPrevious = { goToPage(pagerState.currentPage - 1) }
        pipActions.onNext = { goToPage(pagerState.currentPage + 1) }
        pipActions.hasPrevious = pagerState.currentPage > 0
        pipActions.hasNext = pagerState.currentPage < items.lastIndex
        hotKeyTarget.bind(
            playerProvider = { pagePlayers[pagerState.currentPage] },
            playPrevious = { goToPage(pagerState.currentPage - 1) },
            playNext = { goToPage(pagerState.currentPage + 1) },
            quickPlaybackSpeed = Preferences.playbackSpeed.get()
        )
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
    // 侧栏点击 → 跳转到对应页
    LaunchedEffect(scrollToIndex) {
        if (scrollToIndex in items.indices) {
            pagerState.animateScrollToPage(scrollToIndex)
            onScrollRequestHandled()
        }
    }
    // 内容区（让位 + 淡出）由外壳的 `FlowContentSide` 容器承担，背景层由 `FlowBackground`
    // 插槽提供；本页只负责业务：分页 + 每页的播放 / 手势 / 控件 / 字幕 / 弹窗。
    VerticalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        // 与播放器池的保活 ±1 对齐
        beyondViewportPageCount = 1,
        // 页身份 = 媒体身份：item remove 后 Pager 才能按身份复用 / 回收页（而非按下标错位）
        key = { page -> items[page].mediaId }
    ) { page ->
        FlowVideoPageItem(
            controller = controller,
            page = page,
            media = items[page],
            isCurrent = page == pagerState.currentPage,
            isControlVisible = isDecorationVisible,
            isPipMode = isPipMode,
            quickSpeedLabel = quickSpeedLabel,
            isArchiveEnabled = isArchiveEnabled,
            pipActions = pipActions,
            onPlayerRegistered = { exo -> pagePlayers[page] = exo },
            onPageReleased = { pagePlayers.remove(page) },
            onChangeDecoration = onChangeDecoration,
            onArchiveClick = onArchiveClick,
            // 非循环模式下播完自动切下一页（对齐旧 VideoFlowActivity.onVideoPlayEnd）
            onPlaybackEnded = {
                val next = page + 1
                if (next <= items.lastIndex) {
                    scope.launch { pagerState.animateScrollToPage(next) }
                }
            }
        )
    }
}

/** 单页：内容区 + 手势 + 控件层 + 字幕层 + 选速 / 选轨弹窗。 */
@Composable
private fun FlowVideoPageItem(
    controller: FlowPlaybackController,
    page: Int,
    media: LMedia,
    isCurrent: Boolean,
    isControlVisible: Boolean,
    isPipMode: Boolean,
    quickSpeedLabel: String,
    isArchiveEnabled: Boolean,
    pipActions: FlowPipActions,
    onPlayerRegistered: (ExoPlayer?) -> Unit,
    onPageReleased: () -> Unit,
    onChangeDecoration: (Boolean) -> Unit,
    onArchiveClick: (LMedia, ArchiveQuick) -> Unit,
    /** 当前页播放自然结束时回调（非循环模式由页面切到下一页；单曲播放页可忽略）。 */
    onPlaybackEnded: () -> Unit = {}
) {
    val gesture = rememberFlowGestureState()
    val context = LocalContext.current
    // 手势触觉反馈：对齐旧 View 版（`FlowPlayerGestureHost` 捕获时 LONG_PRESS、
    // `VideoPlayHolder.startTouchPlaybackSpeed / startTouchSeekMode` 用 GESTURE_START/END）。
    // Compose 侧统一用 HapticFeedbackType，两处「进入」与两处「结束」各给一次。
    val haptics = LocalHapticFeedback.current
    /** 双击快捷跳转的边缘热区宽度（px）：**固定 [DoubleTapEdgeWidth]**，不随屏幕尺寸变化。 */
    val doubleTapEdgeWidthPx = with(LocalDensity.current) { DoubleTapEdgeWidth.toPx() }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    // 预设倍速的显示文本：1 倍速时作为「待启用」提示常显（旧 `PlaybackSpeed.init` 同义）
    val presetSpeedLabel = remember(quickSpeedLabel) {
        quickSpeedLabel.ifEmpty { FlowPlaybackSpeed.display(Preferences.playbackSpeed.get()) }
    }
    var speedLabel by remember(presetSpeedLabel) { mutableStateOf(presetSpeedLabel) }
    var currentSpeed by remember { mutableFloatStateOf(1F) }
    // 长按变速前的速度（松手恢复用）：长按期间进度轮询会把 currentSpeed 写成变速值，
    // 因此恢复不能依赖 currentSpeed，必须独立记录进入前的值。
    var speedBeforeGesture by remember { mutableFloatStateOf(1F) }
    // 快捷跳转步长（秒）：进度条两侧按钮与「双击两侧」共用同一份偏好时间。
    val quickSeekStepSeconds = remember { Preferences.quickForwardTime.get().toInt() }
    val quickSeekOffsetMs = quickSeekStepSeconds * 1000L
    // 按钮的显隐由「快捷跳转按钮」开关决定；「双击两侧」由另一独立开关决定，两者互不影响。
    val quickSeekSeconds = if (Preferences.isQuickForwardEnable.get()) quickSeekStepSeconds else null
    // 续播起点：本页首次装载该媒体时读取一次（之后位置由播放器自身维持）。
    // 进度取自进程内的 LRU 表（键为 uri），与来源类型无关。
    val startPositionMs = remember(media) { PlaybackProgressStore.get(media.uri) }
    // 播放器状态：双击播放的「安全防御」据此判断能否播放（对齐旧实现只在 Paused / Ready 时 play）
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    // 字幕是否处于「开启」态：决定字幕按钮显示实心 / 划线图标（对齐旧 onTracksChanged）
    var isSubtitleEnabled by remember { mutableStateOf(false) }
    var hasSubtitle by remember { mutableStateOf(false) }
    var trackGroup by remember { mutableStateOf<VideoTrackGroup?>(null) }
    var showSpeedPicker by remember { mutableStateOf(false) }
    var showSubtitlePicker by remember { mutableStateOf(false) }
    // 触摸进度模式
    var isTouchSeek by remember { mutableStateOf(false) }
    var seekStartMs by remember { mutableLongStateOf(0L) }
    var osdPrecision by remember { mutableFloatStateOf(1F) }
    // 滑块拖动
    var isSliding by remember { mutableStateOf(false) }
    var slideProgress by remember { mutableFloatStateOf(0F) }
    // 双击快捷跳转：以内容区宽度三等分，左右两侧分别为快退 / 快进，中间保持播放暂停。
    // 轻触位置由手势层上报（`onTapAction`）；宽度取内容区实测值（侧栏让位时会变窄）。
    var viewportWidthPx by remember { mutableFloatStateOf(0F) }
    var lastTapX by remember { mutableFloatStateOf(0F) }

    /**
     * 按给定毫秒偏移跳转（正快进 / 负快退），并夹取到 `[0, duration]`。
     *
     * 「进度条两侧按钮」与「双击两侧」共用此实现，保证两者行为完全一致——包括触觉反馈：
     * 两条路径都是「一次明确的跳转动作」，在真正执行跳转时给一次震动。
     */
    fun seekBy(deltaMs: Long) {
        val exo = player ?: return
        if (deltaMs == 0L) {
            return
        }
        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
        val duration = exo.duration.coerceAtLeast(0L)
        val target = (exo.currentPosition + deltaMs).coerceAtLeast(0L)
        val clamped = if (duration > 0L) target.coerceAtMost(duration) else target
        exo.seekTo(clamped)
        positionMs = clamped.coerceAtLeast(0L)
    }

    // 登记 / 注销本页 player：PiP 与热键按「当前页」取用
    LaunchedEffect(player) {
        onPlayerRegistered(player)
    }
    DisposableEffect(page) {
        onDispose { onPageReleased() }
    }

    // 播放自然结束：非循环模式下由页面切到下一页
    DisposableEffect(player, isCurrent) {
        val exo = player
        if (exo == null || !isCurrent) {
            return@DisposableEffect onDispose { }
        }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && !Preferences.isLoopPlayback.get()) {
                    onPlaybackEnded()
                }
            }
        }
        exo.addListener(listener)
        onDispose { exo.removeListener(listener) }
    }

    // 播放错误：给出可见提示，不静默（对齐旧 `VideoPlayHolder.onPlayerError` → Toast）
    DisposableEffect(player) {
        val exo = player
        if (exo == null) {
            return@DisposableEffect onDispose { }
        }
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val tip = "Code: ${error.errorCodeName}\n${error.message ?: "Unknown"}"
                Toast.makeText(context, tip, Toast.LENGTH_LONG).show()
            }
        }
        exo.addListener(listener)
        onDispose { exo.removeListener(listener) }
    }

    val clickCounter = rememberFlowClickCounter { count ->
        // 触摸 seek / 变速手势进行中不接受点击（对齐旧 `onClick` 的 isTouchSeekMode 早退）
        if (isTouchSeek) {
            return@rememberFlowClickCounter
        }
        when (count) {
            1 -> onChangeDecoration(!isControlVisible)
            2 -> {
                onChangeDecoration(true)
                // 左右两侧双击 → 快捷跳转（与进度条两侧按钮同一步长，但由独立开关控制）；
                // 中间双击 → 保持原有的「播放 / 暂停」。宽度未知时按中间处理。
                val quickSeekEnabled = Preferences.isDoubleTapQuickSeekEnable.get() && quickSeekOffsetMs > 0L
                // 边缘热区按**固定 58dp** 判定（不用百分比：平板上按比例会宽得离谱）
                val atLeftEdge = viewportWidthPx > 0F && lastTapX <= doubleTapEdgeWidthPx
                val atRightEdge = viewportWidthPx > 0F && lastTapX >= viewportWidthPx - doubleTapEdgeWidthPx
                when {
                    quickSeekEnabled && atLeftEdge -> seekBy(-quickSeekOffsetMs)
                    quickSeekEnabled && atRightEdge -> seekBy(quickSeekOffsetMs)
                    else -> player?.let { exo ->
                        // 中间双击：播放 / 暂停，同样给一次触觉确认
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        if (exo.isPlaying) {
                            exo.pause()
                        } else if (
                            exo.playbackState == Player.STATE_READY ||
                            exo.playbackState == Player.STATE_BUFFERING
                        ) {
                            // 安全防御：仅在「已就绪 / 缓冲中」才 play()，对齐旧实现只在
                            // Paused / Ready 状态播放；避免在 IDLE / ENDED 下触发无意义的播放
                            exo.play()
                        }
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
            yMaxRangeRatio = Preferences.videoTouchMaxRangeRatioY.get(),
            onStartSpeed = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                speedBeforeGesture = currentSpeed
                player?.setPlaybackSpeed(Preferences.playbackSpeed.get())
            },
            onStopSpeed = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                player?.setPlaybackSpeed(speedBeforeGesture)
                currentSpeed = speedBeforeGesture
            },
            onStartSeek = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
                    val target = (seekStartMs + weight * durationMs).toLong().coerceIn(0L, durationMs)
                    exo.seekTo(target)
                    // 手势 seek 期间轮询不会覆盖进度（见 isTouchSeek 判定），这里主动同步，
                    // 让 OSD 刻度与时间文本跟随手势一起滚动
                    positionMs = target
                }
            },
            onStopSeek = { weight ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                val exo = player ?: return@FlowSeekGestureListener
                if (durationMs > 0L) {
                    val target = (seekStartMs + weight * durationMs).toLong().coerceIn(0L, durationMs)
                    exo.seekTo(target)
                    positionMs = target
                }
                isTouchSeek = false
                exo.play()
            },
            onTapAction = { x ->
                lastTapX = x
                clickCounter.click()
            }
        )
    }

    // 进度轮询：仅当前页（省电），同时刷新倍速 / 字幕轨道 / PiP 所需状态
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
            playbackState = exo.playbackState
            val speed = exo.playbackParameters.speed
            currentSpeed = speed
            // 标签文本随播放参数刷新（对齐旧 PlaybackSpeed.onSpeedChanged(PlaybackParameters)）
            speedLabel = if (FlowPlaybackSpeed.isNormalSpeed(speed)) {
                presetSpeedLabel
            } else {
                FlowPlaybackSpeed.display(speed)
            }
            hasSubtitle = exo.currentTracks.groups.any { it.type == C.TRACK_TYPE_TEXT }
            isSubtitleEnabled = exo.currentTracks.groups.any {
                it.type == C.TRACK_TYPE_TEXT && it.isSelected
            }
            if (hasSubtitle) {
                trackGroup = findSubtitleTracks(exo.currentTracks)
            }
            // 进度记忆：当前页持续刷新缓存（剩余 > 1s 才记，见 updatePlaybackProgress）
            media.updatePlaybackProgress(exo.currentPosition.coerceAtLeast(0L), durationMs)
            pipActions.isPlaying = isPlaying
            val videoSize = exo.videoSize
            pipActions.videoWidth = videoSize.width
            pipActions.videoHeight = videoSize.height
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
            .onSizeChanged { viewportWidthPx = it.width.toFloat() }
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
            startPositionMs = startPositionMs,
            isBlurBackgroundEnabled = Preferences.isBlurVideoBackground.get(),
            onPlayerReady = { exo -> player = exo }
        )

        // 字幕独立于控件显隐
        FlowSubtitleOverlay(
            player = player,
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            // 画中画下隐藏控件层（对齐旧 `controllerVisibleFilter.onPipChanged`）
            visible = isControlVisible && !isPipMode,
            // AnimatedVisibility 默认的 enter / exit 含 expandIn / shrinkOut，
            // 观感是「整体向左上角收起」；这里只要透明度渐变
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            FlowVideoControlLayer(
                state = FlowVideoControlState(
                    progress = progress,
                    progressText = progressText(positionMs, durationMs),
                    showProgressText = Preferences.isShowVideoProgressText.get(),
                    isPaused = !isPlaying,
                    quickSpeedLabel = speedLabel,
                    // 可见性由偏好开关决定（旧 `playbackSpeedVisibleFilter`），
                    // 颜色区分当前是否已启用非 1 倍速
                    showQuickSpeed = Preferences.isShowSpeedBtn.get(),
                    quickSpeedEnabled = !FlowPlaybackSpeed.isNormalSpeed(currentSpeed),
                    showSubtitleButton = hasSubtitle,
                    subtitleEnabled = isSubtitleEnabled,
                    archiveActions = archiveActions,
                    quickSeekSeconds = quickSeekSeconds,
                    rewindLabel = if (isTouchSeek) "-${seekOffsetLabel(seekStartMs, positionMs)}" else "",
                    forwardLabel = if (isTouchSeek) "+${seekOffsetLabel(seekStartMs, positionMs)}" else "",
                    osd = if (isTouchSeek && Preferences.isShowSeekOsd.get()) {
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
                    // 短按：1 倍速与偏好倍速之间切换（对齐旧 PlaybackSpeed.toggleSpeed）
                    val exo = player ?: return@FlowVideoControlLayer
                    val target = if (FlowPlaybackSpeed.isNormalSpeed(exo.playbackParameters.speed)) {
                        Preferences.playbackSpeed.get()
                    } else {
                        1F
                    }
                    exo.setPlaybackSpeed(target)
                    currentSpeed = target
                    speedLabel = FlowPlaybackSpeed.display(target)
                },
                onQuickSpeedLongClick = {
                    showSpeedPicker = true
                },
                onSubtitleClick = {
                    showSubtitlePicker = true
                },
                onArchiveClick = { quick -> onArchiveClick(media, quick) },
                onQuickRewind = { seekBy(-quickSeekOffsetMs) },
                onQuickForward = { seekBy(quickSeekOffsetMs) },
                sliderTouchMode = if (Preferences.isVideoSliderTapEnable.get()) {
                    FlowSliderTouchMode.Tap
                } else {
                    FlowSliderTouchMode.Drag
                }
            )
        }

        if (showSpeedPicker) {
            val current = currentSpeed
            FlowChoiceDialog(
                items = FlowPlaybackSpeed.entries.map { item ->
                    FlowChoiceItem(
                        label = stringResource(item.label),
                        selected = item.speed == current
                    )
                },
                onSelect = { index ->
                    val speed = FlowPlaybackSpeed.entries[index].speed
                    showSpeedPicker = false
                    // 与旧实现一致：选择后写入偏好（下次进入沿用），并立即应用
                    Preferences.playbackSpeed.set(speed)
                    player?.setPlaybackSpeed(speed)
                    currentSpeed = speed
                    speedLabel = FlowPlaybackSpeed.display(speed)
                },
                onDismiss = { showSpeedPicker = false }
            )
        }

        if (showSubtitlePicker) {
            val group = trackGroup
            if (group == null) {
                showSubtitlePicker = false
            } else {
                val closeLabel = stringResource(com.lollipop.mediaflow.R.string.label_close_subtitle_on_play)
                FlowChoiceDialog(
                    items = buildList {
                        add(FlowChoiceItem(label = closeLabel, selected = !group.enable))
                        group.tracks.forEach { track ->
                            add(
                                FlowChoiceItem(
                                    label = track.label.ifEmpty { track.language },
                                    description = track.language,
                                    selected = track.isSelected
                                )
                            )
                        }
                    },
                    onSelect = { index ->
                        showSubtitlePicker = false
                        val chosen: VideoTrack? = if (index == 0) {
                            null
                        } else {
                            group.tracks.getOrNull(index - 1)
                        }
                        player?.applySubtitleSelection(chosen)
                    },
                    onDismiss = { showSubtitlePicker = false }
                )
            }
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

/**
 * 双击快捷跳转的边缘热区宽度：距内容区左右边缘**各 58dp**。
 *
 * 为什么用固定 dp 而不是百分比：热区要对应「手指够得着的边缘」，与屏幕尺寸无关。
 * 按百分比在手机上尚可，在平板上会把热区放大到十几厘米，中间的「播放 / 暂停」被挤压。
 *
 * 语义（以内容区左边界为 0）：
 * - 左侧 `[0, 58dp]` 双击 → 快退 `Preferences.quickForwardTime` 秒；
 * - 右侧 `[宽 − 58dp, 宽]` 双击 → 快进同样秒数；
 * - 其余（大部分区域）双击 → 播放 / 暂停。
 */
private val DoubleTapEdgeWidth = 58.dp

/** 进度轮询间隔。 */
private const val ProgressPollIntervalMs = 200L

/** 无跳转请求。 */
private const val NoScrollRequest = -1
