package com.lollipop.auditory.audio

import android.content.ComponentName
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.lollipop.auditory.tool.PermissionLauncher
import com.lollipop.common.tools.LLog.Companion.registerLog

val LocalAudioController = staticCompositionLocalOf<AudioController> {
    error("No AudioController provided")
}

class AudioController(private val delegate: AudioControllerDelegate) {

    private val log = registerLog()

    fun option(callback: (Player) -> Unit) {
        val controller = delegate.mediaController
        if (controller == null) {
            log.w("option: controller is null")
        } else {
            callback(controller)
        }
    }

}

class AudioControllerDelegate(
    private val activity: ComponentActivity
) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null
        private set

    private val audioEventObserver = AudioEventObserver(::requestCurrentPosition)

    private val log = registerLog()

    private fun startService() {
        if (PermissionLauncher.Permission.Notification.check(activity)) {
            val intent = Intent(activity, AudioService::class.java).apply {
                // 可以携带一些基本指令，或者单纯作为一个启动信号
                action = AudioService.ACTION_PLAY_MUSIC
            }
            // 显式手动启动服务，将其生命周期与 Activity 脱离，变成独立运行
            AudioServiceHelper.startForegroundService(activity, intent)
        }
    }

    val controller = AudioController(this)

    fun onStart() {

        startService()

        // 1. 指定你要连接的后台 Service
        val sessionToken = SessionToken(activity, ComponentName(activity, AudioService::class.java))

        // 2. 异步建立连接
        controllerFuture = MediaController.Builder(activity, sessionToken).buildAsync()

        controllerFuture?.addListener({
            // 3. 连接成功，获取 Controller 实例
            mediaController = controllerFuture?.get()

            // 4. 此时 mediaController 已经自动同步了 Service 里的播放状态！
            onControllerChanged(mediaController)

            // 5. 挂载监听器，实时接收 Service 发来的状态更新（如切歌、暂停、缓冲）
            mediaController?.addListener(audioEventObserver.playerListener)

        }, MoreExecutors.directExecutor())
    }

    private fun requestCurrentPosition(): Long {
        try {
            val exoPlayer = mediaController ?: return 0
            if (!exoPlayer.isConnected) {
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

    private fun onControllerChanged(player: Player?) {
        // 在这里直接读取当前歌曲信息，即便 Activity 刚打开也能立刻拿到最新数据
        val currentMediaItem = player?.currentMediaItem
        // TODO()
    }

    fun onStop() {
        // Activity 不可见或销毁时，解除绑定。此时 Service 在后台继续播放，不受任何影响
        mediaController?.removeListener(audioEventObserver.playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
    }

}