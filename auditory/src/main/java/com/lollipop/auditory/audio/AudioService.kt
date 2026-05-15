package com.lollipop.auditory.audio

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.lollipop.auditory.R
import com.lollipop.common.tools.LLog.Companion.registerLog

class AudioService : MediaSessionService() {

    companion object {
        const val ACTION_PLAY_MUSIC = "ACTION_PLAY_MUSIC"
    }

    private var mediaSession: MediaSession? = null
    private val audioEventObserver = AudioEventObserver(::requestCurrentPosition)

    private val log = registerLog()

    override fun onCreate() {
        super.onCreate()
        createPlayer()
        createNotificationChannel()
    }

    @OptIn(UnstableApi::class)
    private fun requestCurrentPosition(): Long {
        try {
            val exoPlayer = mediaSession?.player ?: return 0
            if (exoPlayer is ExoPlayer) {
                if (exoPlayer.isReleased) {
                    return 0
                }
            }
            if (exoPlayer.availableCommands.contains(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)) {
                return exoPlayer.currentPosition
            }
        } catch (e: Throwable) {
            log.e("fetchCurrentProgress", e)
        }
        return 0
    }


    @OptIn(UnstableApi::class)
    private fun createPlayer() {
        val player = ExoPlayer.Builder(this)
            .build()
        player.addListener(audioEventObserver.playerListener)
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setAudioOffloadPreferences(
                TrackSelectionParameters.AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(
                        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                    )
                    // Add additional options as needed
                    .setIsGaplessSupportRequired(true)
                    .build()
            )
            .build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    @OptIn(UnstableApi::class)
    private fun createNotificationChannel() {
        // 1. 创建一个自定义的通知提供者
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId("music_playback_channel") // 自定义通知渠道 ID
            .setChannelName(R.string.notification_channel_playback)  // 自定义渠道名称
            .build()
        // 2. 将其设置给 Service，覆盖系统的默认样式
        setMediaNotificationProvider(notificationProvider)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // START_STICKY 确保服务如果被系统意外回收，在内存充足时会自动重启
        return START_STICKY
    }


    override fun onGetSession(p0: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

}