package com.lollipop.mediaflow.page.flow

import android.annotation.SuppressLint
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MetadataLoader
import com.lollipop.mediaflow.databinding.PageVideoFlowBinding
import com.lollipop.mediaflow.tools.ClickHelper
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.mediaflow.tools.VideoTouchHelper
import com.lollipop.mediaflow.ui.view.DeconstructSlider
import com.lollipop.mediaflow.video.VideoController
import com.lollipop.mediaflow.video.VideoListener
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

    val videoListener = object : VideoListener {
        override fun onVideoBegin() {
            videoState = VideoState.Playing
            binding.artworkView.isVisible = false
//            updateProgress(0)
        }

        override fun onVideoProgress(ms: Long) {
            updateProgress(ms)
        }

        override fun onPlay() {
            binding.playButton.isVisible = false
            videoState = VideoState.Playing
        }

        override fun onPause() {
            if (videoState != VideoState.Pending) {
                videoState = VideoState.Paused
                binding.playButton.isVisible = !isTouchSeekMode
            }
        }

        override fun onVideoEnd() {
            videoState = VideoState.Ended
        }

        override fun onPlayerError(msg: String) {
            log.w("onPlayerError: $msg")
            Toast.makeText(itemView.context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    var videoController: VideoController? = null
        set(value) {
            field = value
            onFocusChanged()
        }

    var videoTouchDisplay: VideoTouchDisplay? = null
    private val sliderAnimator: DeconstructSlider.AnimationDelegate

    var changeDecorationCallback: ((Boolean) -> Unit)? = null

    val videoPlayerView: PlayerView
        get() {
            return binding.playerView
        }

    private var isControlVisibility = false

    private var lastChangeTime = 0L
    private var isSliderTouched = false

    init {
        binding.playerView.setOnClickListener(clickHelper)
        sliderAnimator = DeconstructSlider.AnimationDelegate(binding.progressSlider)
        binding.progressSlider.sliderChangeListener =
            object : DeconstructSlider.SliderChangeListener {
                override fun onTouchDown() {
                    isSliderTouched = true
                    binding.progressTextView.isVisible = true
                    val currentTime = (binding.progressSlider.progress * videoLength).toLong()
                    seekTo(currentTime)
                    lastChangeTime = now()
                    updateProgressTextView(currentTime)
                    sliderAnimator.onTouchDown()
                }

                override fun onTouchUp() {
                    binding.progressTextView.isVisible = false
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
                            updateProgressTextView(currentTime)
                        }
                    }
                }
            }
        binding.root.flowTouchListener = videoTouchHelper
        initSliderAnimation()
    }

    private fun onFocusChanged() {
        binding.artworkView.isVisible = videoController == null
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

    fun onBind(media: MediaInfo.File) {
        clickHelper.reset()
        Glide.with(itemView)
            .load(media.uri)
            .into(binding.artworkView)
        binding.artworkView.isVisible = true
        binding.playButton.isVisible = false
        videoState = VideoState.Pending
        MetadataLoader.load(itemView.context, media) {
            videoLength = media.metadata?.duration ?: 0
        }
    }

    private fun updateControlVisibility(visible: Boolean) {
        binding.controlLayout.isVisible = visible
        changeDecorationCallback?.invoke(visible)
        isControlVisibility = visible
    }

    private fun onClick(clickCount: Int) {
        if (isTouchSeekMode) {
            return
        }
        if (clickCount == 1) {
            // 点击一次
            updateControlVisibility(!isControlVisibility)
        } else if (clickCount == 2) {
            // 点击两次
            updateControlVisibility(true)
            if (videoState == VideoState.Playing) {
                videoController?.pause()
            } else if (videoState == VideoState.Paused) {
                videoController?.play()
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

    enum class VideoState {
        Pending,
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

}