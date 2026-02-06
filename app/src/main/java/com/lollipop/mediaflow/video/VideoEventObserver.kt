package com.lollipop.mediaflow.video

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.task

class VideoEventObserver(
    private val progressCallback: () -> Long
) {

    private val log = registerLog()

    val playerListener: Player.Listener = PlayerListenerWrapper(this)

    var focusListener: VideoListener? = null
        private set

    var updateInterval = 100L

    private val listenerManager = ArrayList<VideoListener>()

    private var isPlaying = false

    private val progressUpdateTask = task {
        onProgressUpdate()
    }

    fun add(listener: VideoListener) {
        listenerManager.add(listener)
    }

    fun remove(listener: VideoListener) {
        listenerManager.remove(listener)
    }

    fun setFocus(listener: VideoListener?) {
        focusListener = listener
    }

    private fun invoke(block: VideoListener.() -> Unit) {
        focusListener?.block()
        for (listener in listenerManager) {
            listener.block()
        }
    }

    private fun onVideoReady() {
        invoke { onVideoBegin() }
    }

    private fun onVideoEnded() {
        invoke { onVideoEnd() }
    }

    private fun onVideoPlayingChanged(isPlaying: Boolean) {
        this.isPlaying = isPlaying
        progressUpdateTask.cancel()
        onProgressUpdate()
        if (isPlaying) {
            invoke { onPlay() }
        } else {
            invoke { onPause() }
        }
    }

    private fun onPlayerError(error: PlaybackException) {
        val msg = "Code: ${error.errorCodeName}, Message: ${error.message ?: "Unknown"}"
        log.e("onPlayerError", error)
        invoke { onPlayerError(msg) }
    }

    private fun onProgressUpdate() {
        val progress = progressCallback()

        invoke { onVideoProgress(progress) }

        if (isPlaying) {
            progressUpdateTask.delay(updateInterval)
        }
    }

    private class PlayerListenerWrapper(
        private val observer: VideoEventObserver
    ) : Player.Listener {

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_ENDED -> observer.onVideoEnded()
            }
        }

        override fun onRenderedFirstFrame() {
            super.onRenderedFirstFrame()
            observer.onVideoReady()
        }

        override fun onPlayerError(error: PlaybackException) {
            observer.onPlayerError(error)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            observer.onVideoPlayingChanged(isPlaying)
        }

    }

}