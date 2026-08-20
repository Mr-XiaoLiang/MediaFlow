package com.lollipop.mediaflow.page.flow

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.media3.common.PlaybackParameters
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory
import com.lollipop.common.tools.ClickHelper
import com.lollipop.common.tools.LLog.Companion.registerLog
import com.lollipop.common.tools.task
import com.lollipop.common.ui.view.DeconstructSlider
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.data.local.ArchiveQuick
import com.lollipop.mediaflow.data.local.MediaInfo
import com.lollipop.mediaflow.data.local.MetadataLoader
import com.lollipop.mediaflow.databinding.PageVideoFlowBinding
import com.lollipop.mediaflow.page.flow.video.ArchiveDelegate
import com.lollipop.mediaflow.page.flow.video.PlaybackSpeed
import com.lollipop.mediaflow.page.flow.video.SubtitleDelegate
import com.lollipop.mediaflow.tools.DisplayFormater
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.mediaflow.tools.VideoTouchHelper
import com.lollipop.mediaflow.ui.CoverLoader
import com.lollipop.mediaflow.ui.GestureSlideOsdView
import com.lollipop.mediaflow.ui.PipVisibleFilter
import com.lollipop.mediaflow.video.VideoController
import com.lollipop.mediaflow.video.VideoListener
import com.lollipop.mediaflow.video.VideoTrackGroup

class VideoPlayHolder(
    private val binding: PageVideoFlowBinding
) : RecyclerView.ViewHolder(binding.root),
    VideoTouchHelper.VideoController {

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
    var videoProgress: Long = 0
        private set
    private var videoState = VideoState.Pending

    private var isTouchSeekMode = false

    private val videoTouchHelper = VideoTouchHelper(
        baseWeight = Preferences.videoTouchSeekBaseWeight.get(),
        videoController = this,
        xThreshold = ViewConfiguration.get(itemView.context).scaledTouchSlop * 2F,
        yMaxRangeRatio = Preferences.videoTouchMaxRangeRatioY.get(),
        minWeight = 0.05F
    )

    private var videoController: VideoController? = null

    private var videoTouchDisplay: VideoTouchDisplay? = null
    private val sliderAnimator: DeconstructSlider.AnimationDelegate

    private var changeDecorationCallback: DecorationVisibilityCallback? = null

    val videoPlayerView: PlayerView
        get() {
            return binding.playerView
        }

    private var lastChangeTime = 0L
    private var isSliderTouched = false

    private var lastMediaFile: MediaInfo.File? = null

    var archiveEnable = true

    private val quickSeekOffsetValue = Preferences.quickForwardTime.get() * 1000L

    private val playbackSpeed = PlaybackSpeed { color, name ->
        binding.quickPlaybackSpeedButtonText.text = name
        binding.quickPlaybackSpeedButtonText.color = color
    }

    private val deconstructSpeedHelper by lazy {
        DeconstructSpeedHelper(binding.deconstructSpeedTextView)
    }

    private val sliderChangeListener = object : DeconstructSlider.SliderChangeListener {
        override fun onTouchDown() {
            isSliderTouched = true
            val currentTime = (binding.progressSlider.progress * videoLength).toLong()
            seekTo(currentTime)
            lastChangeTime = now()
            updateProgressValue(currentTime)
            sliderAnimator.onTouchDown()
        }

        override fun onTouchUp() {
            seekTo((binding.progressSlider.progress * videoLength).toLong())
            lastChangeTime = now()
            isSliderTouched = false
            sliderAnimator.onTouchUp()
        }

        override fun onProgressChanged(progress: Float, fromUser: Boolean) {
            if (fromUser) {
                val now = now()
                if (now - lastChangeTime > 100) {
                    lastChangeTime = now
                    val currentTime = (videoLength * progress).toLong()
                    seekTo(currentTime)
                    updateProgressValue(currentTime)
                }
            }
        }
    }
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
            videoLength = videoController?.getVideoDuration() ?: videoLength
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
            val notEmpty = tracks.tracks.isNotEmpty()
            subtitleVisibleFilter.preference.setVisible(notEmpty)

            if (notEmpty) {
                binding.subtitleButton.setImageResource(
                    if (tracks.enable) {
                        R.drawable.subtitles_24
                    } else {
                        R.drawable.subtitles_off_24
                    }
                )
            }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            playbackSpeed.onSpeedChanged(playbackParameters)
        }

    }

    private val controllerVisibleFilter = PipVisibleFilter(binding.controlLayout)

    private val archiveDelegate = ArchiveDelegate(::onArchiveClick)

    private val subtitleVisibleFilter = PipVisibleFilter(binding.subtitleButton)
    private val playbackSpeedVisibleFilter = PipVisibleFilter(binding.quickPlaybackSpeedButton)
    private val progressTextVisibleFilter = PipVisibleFilter(binding.progressTextView)

    private fun changeState(tag: String, state: VideoState) {
        val oldState = this.videoState
        this.videoState = state
        log.i("changeState: ${tag}, old = ${oldState}, new = $state")
    }

    init {
        binding.playerView.setOnClickListener(clickHelper)
        sliderAnimator = DeconstructSlider.AnimationDelegate(binding.progressSlider)
        binding.progressSlider.sliderChangeListener = sliderChangeListener

        archiveDelegate.register(binding.archiveFavoriteButton, ArchiveQuick.Favorite)
        archiveDelegate.register(binding.archiveSpecialButton, ArchiveQuick.Special)
        archiveDelegate.register(binding.archiveThumbUpButton, ArchiveQuick.ThumpUp)
        archiveDelegate.register(binding.archiveMoreButton, ArchiveQuick.Other)

        binding.subtitleButton.setOnClickListener {
            showSubtitleSelectDialog()
        }
        binding.quickPlaybackSpeedButton.setOnClickListener {
            videoController?.let {
                playbackSpeed.toggleSpeed(it)
            }
        }
        binding.quickPlaybackSpeedButton.setOnLongClickListener {
            playbackSpeed.showChoosePopup(it)
            true
        }
        binding.gestureHost.also {
            archiveDelegate.registerPenetrate(it)
            it.registerPenetrate(binding.subtitleButton)
            it.registerPenetrate(binding.quickPlaybackSpeedButton)
            it.registerPenetrate(binding.progressSlider)
            it.flowTouchListener = videoTouchHelper
        }

        binding.progressSlider.touchMode = if (Preferences.isVideoSliderTapEnable.get()) {
            DeconstructSlider.TouchMode.Tap
        } else {
            DeconstructSlider.TouchMode.Drag
        }
        val context = itemView.context
        playbackSpeed.init(
            enableColor = context.getColor(R.color.button_slider),
            disableColor = Color.WHITE
        )
        initSliderAnimation()
        initVideoBackground()
        initQuickForward()
    }

    private fun initVideoBackground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding.videoBackground.setRenderEffect(
                RenderEffect.createBlurEffect(50F, 50F, Shader.TileMode.CLAMP)
            )
        }
        // 設置 40% 的黑色遮罩 (十六進制 66 代表約 40% 透明度)
        // #000000 是黑色，SRC_ATOP 會把黑色疊加在圖片上
        binding.videoBackground.setColorFilter(0x66000000, PorterDuff.Mode.SRC_ATOP)
    }

    @SuppressLint("SetTextI18n")
    private fun initQuickForward() {
        if (Preferences.isQuickForwardEnable.get()) {
            val time = Preferences.quickForwardTime.get().toInt()
            binding.rewindGestureView.isVisible = true
            binding.rewindGestureView.text = "-${time}S"
            binding.forwardGestureView.isVisible = true
            binding.forwardGestureView.text = "+${time}S"
            binding.rewindGestureView.setOnClickListener {
                callQuickRewind()
            }
            binding.forwardGestureView.setOnClickListener {
                callQuickForward()
            }
            binding.gestureHost.registerPenetrate(binding.rewindGestureView)
            binding.gestureHost.registerPenetrate(binding.forwardGestureView)
        } else {
            binding.rewindGestureView.isVisible = false
            binding.forwardGestureView.isVisible = false
        }
    }

    private fun updateProgressValue(progress: Long) {
        binding.progressTextView.text = DisplayFormater.formatTime(progress, videoLength)
    }

    /**
     * 快退
     */
    private fun callQuickRewind() {
        videoSeekOffset(quickSeekOffsetValue * -1)
    }

    /**
     * 快进
     */
    private fun callQuickForward() {
        videoSeekOffset(quickSeekOffsetValue)
    }

    private fun videoSeekOffset(offset: Float) {
        videoController?.seekOffset(offset.toInt())
    }

    private fun updateSubtitle() {
        SubtitleDelegate.updateSubtitle(videoPlayerView)
    }

    private fun onArchiveClick(quick: ArchiveQuick) {
        videoTouchDisplay?.onArchiveClick(bindingAdapterPosition, quick)
    }

    private fun showSubtitleSelectDialog() {
        val tracks = currentTracks
        if (tracks == null || tracks.tracks.isEmpty()) {
            return
        }
        val dialog = SubtitleSelectDialog(itemView.context, tracks) {
            videoController?.selectTrack(it)
            updateSubtitle()
            log.i("selectTrack: ${it?.label}")
        }
        dialog.show()
    }

    private fun initSliderAnimation() {
        val context = itemView.context
        val activeColor = context.getColor(R.color.progress_active)
        val inactiveColor = context.getColor(R.color.progress_inactive)
        sliderAnimator.defaultColor(activeColor, inactiveColor)
        sliderAnimator.touchedColor(activeColor, inactiveColor)
        val displayMetrics = context.resources.displayMetrics
        val dp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1F, displayMetrics)
        sliderAnimator.defaultSize(
            active = (4F * dp).toInt(),
            inactive = (2F * dp).toInt(),
            gap = (3F * dp).toInt(),
        )
        sliderAnimator.touchedSize(
            active = (8F * dp).toInt(),
            inactive = (4F * dp).toInt(),
            gap = (6F * dp).toInt(),
        )
    }

    private fun seekTo(value: Long) {
        videoController?.seekTo(value)
    }

    private fun now(): Long {
        return System.currentTimeMillis()
    }

    fun onSelected(isDecorationShown: Boolean) {
        videoProgress = 0
        seekTo(0)
        updateControlVisibility(isDecorationShown)
    }

    private fun updateProgress(ms: Long) {
        // 每20毫秒更新一次进度
        if (videoProgress / 40 != ms / 40) {
            videoProgress = ms
            if (videoLength < 0) {
                videoLength = 0
            }
            if (videoLength == 0L) {
                if (!isSliderTouched) {
                    binding.progressSlider.setProgress(0F)
                }
                return
            }
            if (!isSliderTouched) {
                binding.progressSlider.setProgress(videoProgress * 1F / videoLength)
            }
            updateProgressValue(videoProgress)
            deconstructSpeedHelper.onProgressChanged(ms, videoTouchHelper.lastX.toInt())
        }
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
        updateVisibility(isMediaChanged, media)

        changeState("onBind", VideoState.Pending)
        MetadataLoader.load(itemView.context, media) { metadata ->
            videoLength = metadata?.duration ?: 0
            log.i("onBind: duration = ${metadata?.duration}")
        }
        SubtitleDelegate.postUpdateSubtitle(videoPlayerView)
    }

    private fun updateVisibility(isMediaChanged: Boolean, media: MediaInfo.File) {
        archiveDelegate.updateArchive(archiveEnable)
        updateProgressValue(0)
        playbackSpeedVisibleFilter.preference.setVisible(Preferences.isShowSpeedBtn.get())
        progressTextVisibleFilter.preference.setVisible(Preferences.isShowVideoProgressText.get())
        deconstructSpeedHelper.setEnable(Preferences.isShowSeekOsd.get())
        deconstructSpeedHelper.hide()
        if (isMediaChanged) {
            CoverLoader.load(binding.artworkView, media)
            binding.progressSlider.setProgress(0F)
            binding.artworkView.isVisible = true
            binding.playButton.isVisible = false
            // 确保每次重新绑定都是干净的
            binding.videoBackground.setImageDrawable(null)
            subtitleVisibleFilter.preference.setVisible(false)
            if (Preferences.isBlurVideoBackground.get()) {
                loadBlurBackground(media.uri)
            }
        }
    }

    private fun loadBlurBackground(uri: Uri) {
        Glide.with(itemView)
            .load(uri)
            .override(20)
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

    fun onPipChanged(isInPictureInPictureMode: Boolean) {
        controllerVisibleFilter.onPipChanged(isInPictureInPictureMode)
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

    override fun startTouchPlaybackSpeed() {
        videoController?.startPlaybackSpeed()
        videoTouchDisplay?.startPlaybackSpeed()
        isTouchSeekMode = true
        clickHelper.reset()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            itemView.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
        }
    }

    override fun stopTouchPlaybackSpeed() {
        videoController?.stopPlaybackSpeed()
        videoTouchDisplay?.stopPlaybackSpeed()
        isTouchSeekMode = false
        clickHelper.reset()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            itemView.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
        }
    }

    override fun startTouchSeekMode() {
        videoController?.startSeekMode()
        videoTouchDisplay?.startSeekMode()
        isTouchSeekMode = true
        clickHelper.reset()
        deconstructSpeedHelper.show(videoLength, videoProgress, videoTouchHelper.baseWeight)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            itemView.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
        }
        binding.playButton.isVisible = false
    }

    @SuppressLint("SetTextI18n")
    override fun onTouchSeek(weight: Float, speed: Float) {
        deconstructSpeedHelper.onSpeedChanged(speed)
        videoController?.onTouchSeek(weight = weight, speed = speed)
        videoTouchDisplay?.onTouchSeek(weight = weight, speed = speed)
    }

    override fun stopTouchSeekMode(weight: Float) {
        videoController?.stopSeekMode(weight)
        videoTouchDisplay?.stopSeekMode(weight)
        isTouchSeekMode = false
        deconstructSpeedHelper.hide()
        clickHelper.reset()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            itemView.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
        }
    }

    override fun onScaleGestureChanged(matrix: Matrix) {
        binding.matrixFrameLayout.updateMatrix(matrix)
    }

    private class DeconstructSpeedHelper(
        val view: GestureSlideOsdView
    ) {

        private var isShown = false

        var isEnable = true
            private set

        fun setEnable(enable: Boolean) {
            isEnable = enable
        }

        fun show(videoLength: Long, videoProgress: Long, baseWeight: Float) {
            if (!isEnable) {
                view.isVisible = false
                isShown = false
                return
            }
            isShown = true
            view.isVisible = true
            view.setTotalDuration(videoLength)
            view.setCurrentProgress(videoProgress)
            view.setBaseWeight(baseWeight)
            view.setCurrentPrecision(1F)
        }

        fun onSpeedChanged(speed: Float) {
            if (!isShown) {
                return
            }
            view.setCurrentPrecision(speed)
        }

        fun onProgressChanged(videoProgress: Long, touchX: Int) {
            if (!isShown) {
                return
            }
            view.setCurrentProgress(videoProgress)
            view.setTouchX(touchX)
        }

        fun hide() {
            isShown = false
            view.isVisible = false
        }

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

        fun onTouchSeek(weight: Float, speed: Float)

        fun stopSeekMode(weight: Float)

        fun onArchiveClick(position: Int, quick: ArchiveQuick)
    }

    interface DecorationVisibilityCallback {
        fun changeDecorationVisibility(isShow: Boolean)
    }

}