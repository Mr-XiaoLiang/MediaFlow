package com.lollipop.mediaflow.playback

import androidx.activity.ComponentActivity
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.PlayerPool
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.lollipop.common.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.Preferences
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory

/**
 * Activity 级播放控制器（持 [PlayerPool] 与 [FlowPreloadController]）。
 *
 * ## 职责（对齐计划第三节「分层」）
 * - 承载播放器池 [PlayerPool]，容量与 `VerticalPager` 的保活 ±1（3 个）对齐再冗余 1 个；
 * - 页面通过 `rememberFlowPlayer` 从池中 `acquire` / `yield`，本类**不**插手单页生命周期；
 * - 全局广播倍速 / 循环模式 / 音量（用 [PlayerPool.executeForAll]，覆盖空闲 player）；
 * - `ON_STOP` 暂停、`ON_DESTROY` 释放池与预缓存调度。
 *
 * ## 多播放器 ≠ 同时播放
 * 相邻页各自持有已 prepare 的 player，切页时无需重新缓冲，消除「上一帧停留一下」的空窗；
 * 只有当前页处于播放态。
 *
 * 必须在主线程（Activity `onCreate`）创建：[PlayerPool] 全部方法都要求主线程。
 */
@OptIn(UnstableApi::class)
class FlowPlaybackController(private val activity: ComponentActivity) {

    companion object {

        /** 播放器池容量：保活 ±1（3 个）+ 冗余 1 个。 */
        const val PLAYER_POOL_CAPACITY = 4
    }

    private val log by lazy {
        registerLog()
    }

    /** 所有 player 共用的数据源：远程走缓存，本地直读（[SchemeDataSource]）。 */
    private val dataSourceFactory: DataSource.Factory = SchemeDataSource.Factory(
        cachedFactory = FlowMediaCache.cacheDataSourceFactory(activity),
        localFactory = DefaultDataSource.Factory(activity)
    )

    /** 播放器池：`acquire()` 挂起、`yield(player)` 归还。 */
    val playerPool = PlayerPool(PLAYER_POOL_CAPACITY) { buildPlayer() }

    /** 外圈 ±2 的预缓存调度。 */
    val preload = FlowPreloadController(activity)

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_STOP -> pauseAll()
            Lifecycle.Event.ON_DESTROY -> release()
            else -> {}
        }
    }

    init {
        activity.lifecycle.addObserver(lifecycleObserver)
    }

    /** 广播播放倍速（含空闲 player，保证新 acquire 的 player 也是同一倍速）。 */
    @MainThread
    fun setPlaybackSpeedAll(speed: Float) {
        playerPool.executeForAll { setPlaybackSpeed(speed) }
    }

    /** 广播循环模式。 */
    @MainThread
    fun setRepeatModeAll(mode: Int) {
        playerPool.executeForAll { repeatMode = mode }
    }

    /** 广播音量。 */
    @MainThread
    fun setVolumeAll(volume: Float) {
        playerPool.executeForAll { this.volume = volume }
    }

    @MainThread
    fun pauseAll() {
        playerPool.executeForAll {
            if (playWhenReady || isPlaying) {
                pause()
            }
        }
    }

    /** Activity 销毁时释放池与预缓存；之后不可再 `acquire`。 */
    @MainThread
    fun release() {
        activity.lifecycle.removeObserver(lifecycleObserver)
        preload.release()
        playerPool.release()
        log.i("release")
    }

    /**
     * 建一个 pool 内的 player。
     *
     * - 解码器：沿用既有偏好（nextlib FFmpeg 扩展解码器 / 系统解码器），开启硬解失败降级；
     * - 数据源：统一交给 [SchemeDataSource]（远程缓存、本地直读）；
     */
    private fun buildPlayer(): ExoPlayer {
        val renderersFactory = if (Preferences.useNextPlayerDecoder.get()) {
            NextRenderersFactory(activity)
        } else {
            DefaultRenderersFactory(activity)
        }
        renderersFactory.apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            setEnableDecoderFallback(true)
        }
        val mediaSourceFactory = DefaultMediaSourceFactory(activity)
            .setDataSourceFactory(dataSourceFactory)
        return ExoPlayer.Builder(activity)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                // 循环模式取自偏好（默认单曲循环），与旧 `VideoManager.play` 的口径一致；
                // 非循环时「播完自动切下一个」由页面监听 STATE_ENDED 实现
                repeatMode = if (Preferences.isLoopPlayback.get()) {
                    Player.REPEAT_MODE_ONE
                } else {
                    Player.REPEAT_MODE_OFF
                }
            }
    }
}
