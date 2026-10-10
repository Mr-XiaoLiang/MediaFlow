package com.lollipop.mediaflow.playback

import androidx.media3.exoplayer.ExoPlayer

/**
 * 热键操作目标（**与播放实现无关**）。
 *
 * 旧实现 [`com.lollipop.mediaflow.tools.VideoHotKeyDelegate`] 直接依赖 `VideoManager`，
 * 新链路（播放器池 + 页面索引）无法复用。这里抽出最小操作面，让热键逻辑只认「行为」不认「实现」：
 * - 旧页：由 `VideoManager` 提供（里程碑 9 收敛时再切换）；
 * - 新页：由 [`FlowHotKeyTarget`] 提供（当前页的 player + Pager 翻页）。
 */
interface HotKeyVideoTarget {

    fun isPlaying(): Boolean

    fun play()

    fun pause()

    fun seekTo(ms: Long)

    /** 当前播放位置（毫秒）。 */
    fun currentProgress(): Long

    /** 切到上一项。 */
    fun playPrevious()

    /** 切到下一项。 */
    fun playNext()

    /** 「快速倍速」的目标值（右方向键使用）。 */
    val quickPlaybackSpeed: Float

    /** 开始快速倍速（右方向键按下）。 */
    fun startPlaybackSpeed()

    /** 结束快速倍速（右方向键抬起）。 */
    fun stopPlaybackSpeed()
}

/**
 * 新链路（播放器池）的热键目标。
 *
 * 播放操作都作用于「当前页的 player」；翻页交给页面提供的回调（驱动 `VerticalPager`）。
 * 进度读取直接用 `Player.currentPosition`，因此不需要旧实现里的 `notifyProgressUpdate`。
 */
class FlowHotKeyTarget : HotKeyVideoTarget {

    private var playerProvider: () -> ExoPlayer? = { null }

    private var previousAction: () -> Unit = {}

    private var nextAction: () -> Unit = {}

    private var quickSpeed: Float = 2F

    /** 绑定当前页 player 的取用方式与翻页动作（由页面在组合期刷新）。 */
    fun bind(
        playerProvider: () -> ExoPlayer?,
        playPrevious: () -> Unit,
        playNext: () -> Unit,
        quickPlaybackSpeed: Float
    ) {
        this.playerProvider = playerProvider
        this.previousAction = playPrevious
        this.nextAction = playNext
        this.quickSpeed = quickPlaybackSpeed
    }

    override fun isPlaying(): Boolean {
        return playerProvider()?.isPlaying == true
    }

    override fun play() {
        playerProvider()?.play()
    }

    override fun pause() {
        playerProvider()?.pause()
    }

    override fun seekTo(ms: Long) {
        playerProvider()?.seekTo(ms)
    }

    override fun currentProgress(): Long {
        return playerProvider()?.currentPosition ?: 0L
    }

    override fun playPrevious() {
        previousAction()
    }

    override fun playNext() {
        nextAction()
    }

    override val quickPlaybackSpeed: Float
        get() = quickSpeed

    override fun startPlaybackSpeed() {
        playerProvider()?.setPlaybackSpeed(quickSpeed)
    }

    override fun stopPlaybackSpeed() {
        playerProvider()?.setPlaybackSpeed(1F)
    }
}
