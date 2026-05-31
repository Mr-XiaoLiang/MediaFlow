package com.lollipop.mediaflow.page.flow

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.view.isVisible
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory
import com.lollipop.common.tools.ClickHelper
import com.lollipop.common.tools.LLog.Companion.registerLog
import com.lollipop.common.tools.task
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MetadataLoader
import com.lollipop.mediaflow.databinding.PageVideoFlowBinding
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.mediaflow.tools.VideoTouchHelper
import com.lollipop.mediaflow.ui.CoverLoader
import com.lollipop.mediaflow.ui.PipVisibleFilter
import com.lollipop.mediaflow.video.VideoController
import com.lollipop.mediaflow.video.VideoListener
import com.lollipop.mediaflow.video.VideoTrackGroup
import kotlin.math.max
import kotlin.math.min

class VideoPlayHolder(
    private val binding: PageVideoFlowBinding
) : RecyclerView.ViewHolder(binding.root), VideoTouchHelper.VideoController {

    companion object {
        fun create(layoutInflater: LayoutInflater, parent: ViewGroup? = null): VideoPlayHolder {
            return VideoPlayHolder(
                if (parent == null) {
                    PageVideoFlowBinding.inflate(layoutInflater)
                } else {
                    PageVideoFlowBinding.inflate(layoutInflater, parent, false)
                }
            )
        }
    }

    private val log = registerLog()

    private val clickHelper = ClickHelper(onClick = ::onClick)

    private var videoLength: Long = 0
    private var videoProgress: Long = 0
    private var videoState = VideoState.Pending

    private var isTouchSeekMode = false

    private var videoTouchHelper = VideoTouchHelper(
        baseWeight = Preferences.videoTouchSeekBaseWeight.get(),
        videoController = this,
        xThreshold = ViewConfiguration.get(itemView.context).scaledTouchSlop * 2F,
        yMaxRangeRatio = Preferences.videoTouchMaxRangeRatioY.get(),
        minWeight = 0.05F
    )

    private var videoController: VideoController? = null

    private var videoTouchDisplay: VideoTouchDisplay? = null

    private var changeDecorationCallback: DecorationVisibilityCallback? = null

    val videoPlayerView: PlayerView
        get() {
            return binding.playerView
        }

    private var lastMediaFile: MediaInfo.File? = null

    var archiveEnable = true

    private val delayHideArtworkTask = task {
        binding.artworkView.isVisible = false
    }

    private var currentTracks: VideoTrackGroup? = null

    val videoListener = object : VideoListener {
        override fun onVideoBegin() {
            changeState(
                "onVideoBegin",
                if (videoController?.isPlaying() == true) {
                    VideoState.Playing
                } else {
                    VideoState.Ready
                }
            )
            delayHideArtworkTask.delayOnUI(12)
            updateSubtitle()
        }

        override fun onVideoProgress(ms: Long) {
            updateProgress(ms)
        }

        override fun onPlay() {
            binding.playButton.isVisible = false
            changeState("onPlay", VideoState.Playing)
        }

        override fun onPause() {
            log.i("onPause")
            if (videoState != VideoState.Pending) {
                changeState("onPause", VideoState.Paused)
                binding.playButton.isVisible = !isTouchSeekMode
            }
        }

        override fun onVideoEnd() {
            changeState("onVideoEnd", VideoState.Ended)
        }

        override fun onPlayerError(msg: String) {
            log.w("onPlayerError: $msg")
            Toast.makeText(itemView.context, msg, Toast.LENGTH_SHORT).show()
        }

        override fun onTracksChanged(tracks: VideoTrackGroup) {
            log.i("onTracksChanged: size = ${tracks.tracks.size}, enable = ${tracks.enable}")
            currentTracks = tracks
        }
    }

    private val controllerVisibleFilter = PipVisibleFilter(binding.controlLayout)

    private fun changeState(tag: String, state: VideoState) {
        val oldState = this.videoState
        this.videoState = state
        log.i("changeState: ${tag}, old = ${oldState}, new = $state")
    }

    init {
        binding.playerView.setOnClickListener(clickHelper)
        binding.gestureHost.also {
            it.registerPenetrate(binding.backGestureSpace)
            it.flowTouchListener = videoTouchHelper
        }
        initVideoBackground()
    }

    private fun initVideoBackground() {
        // 設置 40% 的黑色遮罩 (十六進制 66 代表約 40% 透明度)
        // #000000 是黑色，SRC_ATOP 會把黑色疊加在圖片上
        binding.videoBackground.setColorFilter(0x66000000, PorterDuff.Mode.SRC_ATOP)
    }

    @OptIn(UnstableApi::class)
    private fun updateSubtitle() {
        // 在初始化 PlayerView 时设置
        videoPlayerView.subtitleView?.let {
            it.setViewType(SubtitleView.VIEW_TYPE_CANVAS)
            it.setStyle(
                CaptionStyleCompat(
                    // 字体颜色
                    Color.WHITE,
                    // 背景颜色（设为透明更现代）
                    Color.TRANSPARENT,
                    // 窗口颜色
                    Color.TRANSPARENT,
                    // 边缘效果：外阴影
                    CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                    // 阴影颜色
                    Color.BLACK,
                    // 字体样式
                    Typeface.DEFAULT
                )
            )
            // 设置字幕大小（比例单位）
            val playerWidth = it.width
            val playerHeight = it.height
            val subtitleWeight = if (playerWidth > playerHeight) {
                1F
            } else {
                0.6F
            }
            it.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subtitleWeight)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateProgressTextView(currentTime: Long) {
        if (binding.progressTextView.isVisible) {
            val current = max(0, min(currentTime, videoLength))
            binding.progressTextView.text =
                "${formatTime(current)} / ${formatTime(videoLength)}"
        }
    }

    private fun seekTo(value: Long) {
        videoController?.seekTo(value)
    }

    fun onSelected(isDecorationShown: Boolean) {
        videoProgress = 0
        seekTo(0)
        updateControlVisibility(isDecorationShown)
    }

    private fun updateProgress(ms: Long) {
        // 每20毫秒更新一次进度
        if (videoProgress / 100 != ms / 100) {
            videoProgress = ms
            if (videoLength < 0) {
                videoLength = 0
            }
            updateProgressTextView(videoProgress)
        }
    }

    private fun formatTime(ms: Long): String {
        val minutes = ms / 60000
        val seconds = (ms / 1000) % 60
        if (seconds < 10) {
            return "${minutes}:0${seconds}"
        }
        return "${minutes}:${seconds}"
    }

    fun onInsetsChanged(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        binding.controlLayout.setPadding(left, top, right, bottom)
    }

    fun onFocusChange(
        controller: VideoController?,
        touchDisplay: VideoTouchDisplay?,
        decorationCallback: DecorationVisibilityCallback?,
    ) {
        this.videoController = controller
        this.videoTouchDisplay = touchDisplay
        this.changeDecorationCallback = decorationCallback
        binding.artworkView.isVisible = videoController == null
    }

    fun resetScaleGesture() {
        binding.gestureHost.resetScaleGesture()
    }

    fun onBind(media: MediaInfo.File) {
        val isMediaChanged = lastMediaFile !== media
        lastMediaFile = media
        clickHelper.reset()
        resetScaleGesture()
        if (isMediaChanged) {
            CoverLoader.load(binding.artworkView, media)
            binding.artworkView.isVisible = true
            binding.playButton.isVisible = false
        }
        changeState("onBind", VideoState.Pending)
        MetadataLoader.load(itemView.context, media) { metadata ->
            videoLength = metadata?.duration ?: 0
            log.i("onBind: duration = ${metadata?.duration}")
        }
        binding.root.post {
            updateSubtitle()
        }
        if (isMediaChanged) {
            // 确保每次重新绑定都是干净的
            binding.videoBackground.setImageDrawable(null)
            if (Preferences.isBlurVideoBackground.get()) {
                loadBlurBackground(media.uri)
            }
        }
    }

    private fun loadBlurBackground(uri: Uri) {
        Glide.with(itemView)
            .load(uri)
            .override(20)
//            .transform(BlurTransformation(25)) // ✨ 关键：在这里叠加模糊
            .transition(
                DrawableTransitionOptions.withCrossFade(
                    DrawableCrossFadeFactory.Builder(1000) // 设置时长为 1s
                        .setCrossFadeEnabled(true) // 关键：开启真正的交叉淡入淡出，防止闪烁
                        .build()
                )
            )
            .into(binding.videoBackground)
    }

    private fun updateControlVisibility(visible: Boolean) {
        controllerVisibleFilter.base.setVisible(visible)
        changeDecorationCallback?.changeDecorationVisibility(visible)
    }

    private fun onClick(clickCount: Int) {
        if (isTouchSeekMode) {
            log.i("onClick isTouchSeekMode = true, break")
            return
        }
        when (clickCount) {
            1 -> {
                // 点击一次
                updateControlVisibility(!controllerVisibleFilter.base.isVisible)
                log.i("onClick clickCount == 1")
            }

            2 -> {
                // 点击两次
                log.i("onClick clickCount == 2 videoState = $videoState")
                updateControlVisibility(true)
                val isPlaying = videoController?.isPlaying() ?: false
                if (isPlaying) {
                    videoController?.pause()
                } else if (videoState == VideoState.Paused || videoState == VideoState.Ready) {
                    videoController?.play()
                }
            }

            3 -> {
                resetScaleGesture()
            }
        }
    }

    override fun startPlaybackSpeed() {
        videoController?.startPlaybackSpeed()
        videoTouchDisplay?.startPlaybackSpeed()
        isTouchSeekMode = true
        clickHelper.reset()
        itemView.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
    }

    override fun stopPlaybackSpeed() {
        videoController?.stopPlaybackSpeed()
        videoTouchDisplay?.stopPlaybackSpeed()
        isTouchSeekMode = false
        clickHelper.reset()
        itemView.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
    }

    override fun startSeekMode() {
        videoController?.startSeekMode()
        videoTouchDisplay?.startSeekMode()
        isTouchSeekMode = true
        clickHelper.reset()
        itemView.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
        binding.playButton.isVisible = false
    }

    override fun onSeek(weight: Float, precision: Float) {
        videoController?.onTouchSeek(weight = weight, precision = precision)
        videoTouchDisplay?.onTouchSeek(weight = weight, precision = precision)
    }

    override fun stopSeekMode(weight: Float) {
        videoController?.stopSeekMode(weight)
        videoTouchDisplay?.stopSeekMode(weight)
        isTouchSeekMode = false
        clickHelper.reset()
        itemView.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
    }

    override fun onScaleGestureChanged(matrix: Matrix) {
        binding.matrixFrameLayout.updateMatrix(matrix)
    }

    enum class VideoState {
        Pending,
        Ready,
        Playing,
        Paused,
        Ended,
    }

    interface VideoTouchDisplay {
        fun startPlaybackSpeed()

        fun stopPlaybackSpeed()

        fun startSeekMode()

        fun onTouchSeek(weight: Float, precision: Float)

        fun stopSeekMode(weight: Float)

    }

    interface DecorationVisibilityCallback {
        fun changeDecorationVisibility(isShow: Boolean)
    }

}