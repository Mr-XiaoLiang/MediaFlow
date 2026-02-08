package com.lollipop.mediaflow.page.flow

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.slider.Slider
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.databinding.PageVideoFlowBinding
import com.lollipop.mediaflow.tools.ClickHelper
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.video.VideoController
import com.lollipop.mediaflow.video.VideoListener
import kotlin.math.max
import kotlin.math.min

class VideoPlayHolder(
    private val binding: PageVideoFlowBinding
) : RecyclerView.ViewHolder(binding.root) {

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

    val videoListener = object : VideoListener {
        override fun onVideoBegin() {
            videoState = VideoState.Playing
            binding.artworkView.isVisible = false
            updateProgress(0)
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
                binding.playButton.isVisible = true
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

    var changeDecorationCallback: ((Boolean) -> Unit)? = null

    val videoPlayerView: PlayerView
        get() {
            return binding.playerView
        }

    private var isControlVisibility = false

    private var lastChangeTime = 0L

    init {
        binding.root.setOnClickListener(clickHelper)
        binding.progressSlider.setLabelFormatter { value ->
            formatTime(value.toLong())
        }
        binding.progressSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                seekTo(slider.value.toLong())
                lastChangeTime = now()
            }

            override fun onStopTrackingTouch(slider: Slider) {
                seekTo(slider.value.toLong())
                lastChangeTime = now()
            }
        })
        binding.progressSlider.addOnChangeListener { slider, value, fromUser ->
            if (fromUser) {
                val now = now()
                if (now - lastChangeTime > 200) {
                    lastChangeTime = now
                    seekTo(value.toLong())
                }
            }
        }
    }

    private fun onFocusChanged() {
        binding.artworkView.isVisible = videoController == null
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

    @SuppressLint("SetTextI18n")
    private fun updateProgress(ms: Long) {
        if (videoState == VideoState.Pending) {
            return
        }
        // 每100毫秒更新一次进度
        if (videoProgress / 100 != ms / 100) {
            videoProgress = ms
            if (videoLength < 0) {
                videoLength = 0
            }
            if (videoLength == 0L) {
                binding.progressSlider.valueTo = 100F
                return
            }
            val current = max(0, min(ms, videoLength))
            binding.progressSlider.valueFrom = 0F
            binding.progressSlider.valueTo = videoLength.toFloat()
            binding.progressSlider.value = current.toFloat()
            binding.progressTextView.text =
                "${formatTime(current)} / ${formatTime(videoLength)}"
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
        media.loadMetadataSync(itemView.context, cacheOnly = false)
        videoLength = media.metadata?.duration ?: 0
    }

    private fun updateControlVisibility(visible: Boolean) {
        binding.controlLayout.isVisible = visible
        changeDecorationCallback?.invoke(visible)
        isControlVisibility = visible
    }

    private fun onClick(clickCount: Int) {
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

    enum class VideoState {
        Pending,
        Playing,
        Paused,
        Ended,
    }

}