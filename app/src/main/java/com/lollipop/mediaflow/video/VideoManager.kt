package com.lollipop.mediaflow.video

import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.ui.PlayerView
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.Preferences

@OptIn(UnstableApi::class)
class VideoManager(
    private val activity: AppCompatActivity
) : VideoController {

    companion object {

        private val LOG by lazy {
            registerLog()
        }

        fun findTrack(tracks: Tracks): List<VideoTrack> {
            val result = mutableListOf<VideoTrack>()
            try {
                for (trackGroup in tracks.groups) {
                    // 筛选出字幕轨道
                    if (trackGroup.type == C.TRACK_TYPE_TEXT) {
                        for (i in 0 until trackGroup.length) {
                            val format = trackGroup.getTrackFormat(i)
                            result.add(
                                VideoTrack(
                                    group = trackGroup.mediaTrackGroup,
                                    index = i,
                                    label = format.label ?: "", // 字幕名称，如 "中文"、"English"
                                    language = format.language ?: "",// 语言代码，如 "zh"、"en"
                                    isSelected = trackGroup.isTrackSelected(i) // 是否正在播放
                                )
                            )
                        }
                    }
                }
            } catch (e: Throwable) {
                LOG.e("findTrack", e)
            }
            return result
        }
    }

    private val log = registerLog()

    private val exoPlayer: ExoPlayer
    private val videoPreload: VideoPreload

    val eventObserver = VideoEventObserver(::fetchCurrentProgress)

    var currentIndex = -1
        private set

    private var playbackSpeed = 2F

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
        playbackSpeed = Preferences.playbackSpeed.get()
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

    override fun isPlaying(): Boolean {
        return exoPlayer.isPlaying
    }

    override fun play() {
        log.i("play")
        exoPlayer.play()
    }

    override fun startPlaybackSpeed() {
        val params = PlaybackParameters(playbackSpeed) // 2.0倍速
        exoPlayer.playbackParameters = params
    }

    override fun stopPlaybackSpeed() {
        val params = PlaybackParameters(1.0f) // 2.0倍速
        exoPlayer.playbackParameters = params
    }

    private var touchSeekStartPosition = 0L

    override fun startSeekMode() {
        touchSeekStartPosition = exoPlayer.currentPosition
        exoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)
        exoPlayer.pause()
    }

    override fun onTouchSeek(weight: Float, precision: Float) {
        seekWithDelta(weight)
    }

    override fun stopSeekMode(weight: Float) {
        exoPlayer.setSeekParameters(SeekParameters.EXACT)
        exoPlayer.play()
    }

    private fun seekWithDelta(weight: Float) {
        // 计算目标位置：起始位置 + (权重 * 总时长)
        val targetPosition = touchSeekStartPosition + (weight * exoPlayer.duration).toLong()
        // 限制在 [0, duration] 范围内
        val coercedPosition = targetPosition.coerceIn(0, exoPlayer.duration)
        // 快速预览寻道
        exoPlayer.seekTo(coercedPosition)
        eventObserver.notifyProgressUpdate()
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

    fun findTrack(): List<VideoTrack> {
        return findTrack(exoPlayer.currentTracks)
    }

    fun selectTrack(track: VideoTrack?) {
        if (track == null) {
            updateTrack {
                it.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            }
            return
        }
        updateTrack {
            it.setOverrideForType(
                TrackSelectionOverride(track.group, track.index)
            )
        }
    }

    private fun updateTrack(block: (TrackSelectionParameters.Builder) -> Unit) {
        try {
            val builder = exoPlayer.trackSelectionParameters.buildUpon()
            block(builder)
            exoPlayer.trackSelectionParameters = builder.build()
        } catch (e: Throwable) {
            log.e("updateTrack", e)
        }
    }

}