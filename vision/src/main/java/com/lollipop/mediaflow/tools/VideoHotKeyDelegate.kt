package com.lollipop.mediaflow.tools

import android.view.Window
import com.lollipop.common.tools.HotKeyHelper
import com.lollipop.mediaflow.video.VideoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

class VideoHotKeyDelegate(
    private val videoManager: VideoManager,
    private val playPrevious: () -> Unit,
    private val playNext: () -> Unit
) {

    companion object {
        fun register(
            window: Window,
            videoManager: VideoManager,
            playPrevious: () -> Unit,
            playNext: () -> Unit
        ): VideoHotKeyDelegate {
            val delegate = VideoHotKeyDelegate(
                videoManager = videoManager,
                playPrevious = playPrevious,
                playNext = playNext
            )
            HotKeyHelper.registerKeyEvent(
                window = window,
                onKeyDown = delegate.hotKeyHelper.keyDownObserver,
                onKeyUp = delegate.hotKeyHelper.keyUpObserver
            )
            return delegate
        }
    }

    private val hotKeyHelper = HotKeyHelper()

    init {
        registerPlayPauseKey()
    }

    private fun registerPlayPauseKey() {
        hotKeyHelper.register(
            keyCode = Preferences.playPauseKeyCode.get(),
            observer = PlayPauseKeyObserver(videoManager)
        )
        hotKeyHelper.register(
            keyCode = Preferences.downKeyCode.get(),
            observer = PlayNextKeyObserver(playNext)
        )
        hotKeyHelper.register(
            keyCode = Preferences.upKeyCode.get(),
            observer = PlayPreviousKeyObserver(playPrevious)
        )
        hotKeyHelper.register(
            keyCode = Preferences.leftKeyCode.get(),
            observer = FastRewindKeyObserver(videoManager)
        )
        hotKeyHelper.register(
            keyCode = Preferences.rightKeyCode.get(),
            observer = FastForwardKeyObserver(videoManager)
        )
    }

    private class PlayPauseKeyObserver(
        private val videoManager: VideoManager
    ) : HotKeyHelper.KeyObserver {
        override fun onKeyDown(): Boolean {
            if (videoManager.isPlaying()) {
                videoManager.pause()
            } else {
                videoManager.play()
            }
            return true
        }
    }

    private class PlayNextKeyObserver(
        private val playNext: () -> Unit
    ) : HotKeyHelper.KeyObserver {
        override fun onKeyDown(): Boolean {
            playNext()
            return true
        }
    }

    private class PlayPreviousKeyObserver(
        private val playPrevious: () -> Unit
    ) : HotKeyHelper.KeyObserver {
        override fun onKeyDown(): Boolean {
            playPrevious()
            return true
        }
    }

    private class FastRewindKeyObserver(
        private val videoManager: VideoManager
    ) : HotKeyHelper.KeyObserver {

        private val rewindScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        private var isRewinding = false

        private val updateDelay = 500L

        override fun onKeyDown(): Boolean {
            if (isRewinding) {
                return true
            }
            isRewinding = true
            videoManager.pause()
            rewindScope.launch {
                while (isRewinding) {
                    val progress = videoManager.fetchCurrentProgress()
                    if (progress < 1) {
                        isRewinding = false
                        break
                    }
                    val playbackSpeed = videoManager.playbackSpeed
                    val offset = (updateDelay * playbackSpeed * -1).toLong()
                    val newProgress = max(progress + offset, 0L)
                    videoManager.seekTo(newProgress)
                    videoManager.eventObserver.notifyProgressUpdate()
                    delay(updateDelay.milliseconds)
                }
            }
            return true
        }

        override fun onKeyUp(): Boolean {
            isRewinding = false
            videoManager.play()
            return true
        }

    }

    private class FastForwardKeyObserver(
        private val videoManager: VideoManager
    ) : HotKeyHelper.KeyObserver {
        override fun onKeyDown(): Boolean {
            videoManager.startPlaybackSpeed()
            return true
        }

        override fun onKeyUp(): Boolean {
            videoManager.stopPlaybackSpeed()
            return true
        }
    }

}