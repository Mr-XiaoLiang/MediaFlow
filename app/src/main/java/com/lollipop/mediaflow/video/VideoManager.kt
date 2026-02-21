package com.lollipop.mediaflow.video

import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.ui.PlayerView
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog

@OptIn(UnstableApi::class)
class VideoManager(
    private val activity: AppCompatActivity
) : VideoController {

    private val log = registerLog()

    private val exoPlayer: ExoPlayer
    private val videoPreload: VideoPreload

    val eventObserver = VideoEventObserver(::fetchCurrentProgress)

    var currentIndex = -1
        private set

    private var currentLifecycleState: Lifecycle.State = Lifecycle.State.INITIALIZED


    private val lifecycleObserver = LifecycleEventObserver { source, event ->
        currentLifecycleState = source.lifecycle.currentState
        when (event) {
            Lifecycle.Event.ON_CREATE -> onCreate()
            Lifecycle.Event.ON_START -> onStart()
            Lifecycle.Event.ON_RESUME -> onResume()
            Lifecycle.Event.ON_PAUSE -> onPause()
            Lifecycle.Event.ON_STOP -> onStop()
            Lifecycle.Event.ON_DESTROY -> onDestroy()
            else -> {}
        }
    }

    init {
        val preloadStatusControl = VideoPreloadStatusControl(::currentPlayingIndex)
        val preloadBuilder = DefaultPreloadManager.Builder(activity, preloadStatusControl)
        exoPlayer = preloadBuilder.buildExoPlayer()
        videoPreload = VideoPreload(preloadBuilder.build())
        activity.lifecycle.addObserver(lifecycleObserver)
    }

    private fun fetchCurrentProgress(): Long {
        try {
            if (exoPlayer.isReleased) {
                return 0
            }
            if (exoPlayer.availableCommands.contains(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)) {
                return exoPlayer.currentPosition
            }
        } catch (e: Throwable) {
            log.e("fetchCurrentProgress", e)
        }
        return 0
    }

    private fun currentPlayingIndex(): Int {
        return currentIndex
    }

    fun resetMediaList(mediaList: List<MediaInfo.File>, startIndex: Int = 0) {
        log.i("resetMediaList: ${mediaList.size}, $startIndex")
        videoPreload.reset(mediaList, startIndex)
    }

    fun play(index: Int) {
        log.i("play: $index")
        videoPreload.setCurrentIndex(index)
        val source = videoPreload.getSource(index) ?: return
        currentIndex = index
        exoPlayer.setMediaSource(source, false)
        // 单曲循环
        exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
        // 注意：如果之前已经 prepare 过了，且播放器没出错
        // 再次调用 setMediaSource 后，播放器会自动进入准备状态
        // 只有在 IDLE 或 ERROR 状态下才需要重新 prepare()
        if (exoPlayer.playbackState == Player.STATE_IDLE) {
            exoPlayer.prepare()
        }
        play()
    }

    override fun play() {
        log.i("play")
        exoPlayer.play()
    }

    override fun seekTo(ms: Long) {
        log.i("seekTo: $ms")
        exoPlayer.seekTo(ms)
    }

    override fun pause() {
        log.i("pause")
        exoPlayer.pause()
    }

    private fun onCreate() {
        activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        exoPlayer.addListener(eventObserver.playerListener)
        exoPlayer.playWhenReady = true
    }

    fun changeView(oldView: PlayerView?, newView: PlayerView) {
        oldView?.player = null
        newView.player = exoPlayer
    }

    private fun onStart() {
        var index = currentIndex
        if (index < 0) {
            index = 0
        }
        play(index)
    }

    private fun onResume() {
    }

    private fun onPause() {
        pause()
    }

    private fun onStop() {

    }

    private fun onDestroy() {
        exoPlayer.release()
    }

}