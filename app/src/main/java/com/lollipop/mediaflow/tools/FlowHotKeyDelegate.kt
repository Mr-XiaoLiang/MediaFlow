package com.lollipop.mediaflow.tools

import android.view.Window
import com.lollipop.common.tools.HotKeyHelper
import com.lollipop.mediaflow.playback.HotKeyVideoTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

/**
 * 热键委托（**新链路版**，与旧 [`VideoHotKeyDelegate`] 平行）。
 *
 * 与旧实现的差别只有一处：操作目标从 `VideoManager` 换成与实现无关的 [HotKeyVideoTarget]，
 * 因此新页（播放器池）可以直接复用同一套键位语义：
 * - 播放/暂停键：切换播放状态；
 * - 上 / 下：上一项 / 下一项；
 * - 左：长按持续快退（每 [RewindUpdateDelayMs] 回退 `updateDelay × 快速倍速`）；
 * - 右：按下进入快速倍速、抬起恢复 1 倍速。
 *
 * 键位仍取自 [`Preferences`]（与旧实现同一份配置）。
 */
class FlowHotKeyDelegate(private val target: HotKeyVideoTarget) {

    companion object {

        /** 快退的步进间隔（对齐旧实现）。 */
        private const val RewindUpdateDelayMs = 500L

        fun register(window: Window, target: HotKeyVideoTarget): FlowHotKeyDelegate {
            val delegate = FlowHotKeyDelegate(target)
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
        hotKeyHelper.register(
            keyCode = Preferences.playPauseKeyCode.get(),
            observer = PlayPauseKeyObserver(target)
        )
        hotKeyHelper.register(
            keyCode = Preferences.downKeyCode.get(),
            observer = SimpleKeyObserver(target::playNext)
        )
        hotKeyHelper.register(
            keyCode = Preferences.upKeyCode.get(),
            observer = SimpleKeyObserver(target::playPrevious)
        )
        hotKeyHelper.register(
            keyCode = Preferences.leftKeyCode.get(),
            observer = RewindKeyObserver(target)
        )
        hotKeyHelper.register(
            keyCode = Preferences.rightKeyCode.get(),
            observer = SpeedUpKeyObserver(target)
        )
    }

    private class PlayPauseKeyObserver(
        private val target: HotKeyVideoTarget
    ) : HotKeyHelper.KeyObserver {
        override fun onKeyDown(): Boolean {
            if (target.isPlaying()) {
                target.pause()
            } else {
                target.play()
            }
            return true
        }
    }

    private class SimpleKeyObserver(
        private val action: () -> Unit
    ) : HotKeyHelper.KeyObserver {
        override fun onKeyDown(): Boolean {
            action()
            return true
        }
    }

    private class RewindKeyObserver(
        private val target: HotKeyVideoTarget
    ) : HotKeyHelper.KeyObserver {

        private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        private var isRewinding = false

        override fun onKeyDown(): Boolean {
            if (isRewinding) {
                return true
            }
            isRewinding = true
            target.pause()
            scope.launch {
                while (isRewinding) {
                    val progress = target.currentProgress()
                    if (progress < 1) {
                        isRewinding = false
                        break
                    }
                    val offset = (RewindUpdateDelayMs * target.quickPlaybackSpeed * -1).toLong()
                    target.seekTo(max(progress + offset, 0L))
                    delay(RewindUpdateDelayMs.milliseconds)
                }
            }
            return true
        }

        override fun onKeyUp(): Boolean {
            isRewinding = false
            target.play()
            return true
        }
    }

    private class SpeedUpKeyObserver(
        private val target: HotKeyVideoTarget
    ) : HotKeyHelper.KeyObserver {
        override fun onKeyDown(): Boolean {
            target.startPlaybackSpeed()
            return true
        }

        override fun onKeyUp(): Boolean {
            target.stopPlaybackSpeed()
            return true
        }
    }
}
