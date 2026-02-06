package com.lollipop.mediaflow.video

import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MediaType

@OptIn(UnstableApi::class)
class VideoManager(
    private val activity: AppCompatActivity
) {

    private val exoPlayer: ExoPlayer
    private val videoPreload: VideoPreload

    val eventObserver = VideoEventObserver()

    var currentIndex = -1
        private set

    private var pendingIndex = -1

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
        activity.lifecycle.addObserver(lifecycleObserver)
        val preloadStatusControl = VideoPreloadStatusControl(::currentPlayingIndex)
        val preloadBuilder = DefaultPreloadManager.Builder(activity, preloadStatusControl)
        exoPlayer = preloadBuilder.buildExoPlayer()
        videoPreload = VideoPreload(preloadBuilder.build())
    }

    private fun currentPlayingIndex(): Int {
        return currentIndex
    }

    fun resetMediaList(mediaList: List<MediaInfo.File>, startIndex: Int = 0) {
        videoPreload.reset(mediaList, startIndex)
        pendingIndex = startIndex
    }

    fun play(index: Int) {
        videoPreload.setCurrentIndex(index)
        val source = videoPreload.getSource(index)?:return
        currentIndex = index
        exoPlayer.setMediaSource(source)
        exoPlayer.prepare()
        play()
    }

    fun play() {
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
    }

    private fun onCreate() {
        activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        exoPlayer.addListener(eventObserver.playerListener)
        exoPlayer.playWhenReady = true
    }

    private fun onStart() {
        var index = currentIndex
        if (pendingIndex >= 0) {
            index = pendingIndex
        }
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