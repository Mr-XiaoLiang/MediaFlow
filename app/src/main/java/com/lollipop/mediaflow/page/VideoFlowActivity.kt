package com.lollipop.mediaflow.page

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isEmpty
import androidx.core.view.isVisible
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.material.slider.Slider
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MediaStore
import com.lollipop.mediaflow.data.MediaType
import com.lollipop.mediaflow.data.MediaVisibility
import com.lollipop.mediaflow.databinding.PageVideoFlowBinding
import com.lollipop.mediaflow.page.flow.MediaFlowStoreView
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.task
import com.lollipop.mediaflow.ui.BasicFlowActivity
import com.lollipop.mediaflow.video.VideoController
import com.lollipop.mediaflow.video.VideoListener
import com.lollipop.mediaflow.video.VideoManager
import kotlin.math.max
import kotlin.math.min

class VideoFlowActivity : BasicFlowActivity() {

    companion object {

        private const val EXTRA_MEDIA_VISIBILITY = "extra_media_visibility"
        private const val EXTRA_POSITION = "extra_position"

        fun start(context: Context, mediaVisibility: MediaVisibility, position: Int) {
            val intent = Intent(context, VideoFlowActivity::class.java)
            intent.putExtra(EXTRA_MEDIA_VISIBILITY, mediaVisibility.key)
            intent.putExtra(EXTRA_POSITION, position)
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        private fun getMediaVisibility(intent: Intent): MediaVisibility {
            return intent.getStringExtra(EXTRA_MEDIA_VISIBILITY)?.let {
                MediaVisibility.findByKey(it)
            } ?: MediaVisibility.Public
        }

    }

    private val viewPager2 by lazy {
        ViewPager2(this)
    }

    private val mediaData = mutableListOf<MediaInfo.File>()
    private val videoAdapter = PlayAdapter(mediaData)

    private val mediaFlowStoreView by lazy {
        MediaFlowStoreView(::onItemClick)
    }

    private val videoManager by lazy {
        VideoManager(this)
    }

    private var lastHolder: VideoHolder? = null

    private var currentPosition = 0

    private fun findVisibility(): MediaVisibility {
        return getMediaVisibility(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentPosition = intent.getIntExtra(EXTRA_POSITION, 0)
        reloadData()
    }

    private fun onItemClick(mediaInfo: MediaInfo.File, position: Int) {
        setCurrentItem(position)
    }

    private fun optRecyclerView(callback: (RecyclerView) -> Unit) {
        val contentPager = viewPager2
        if (contentPager.isEmpty()) {
            return
        }
        contentPager.getChildAt(0).let { recyclerVier ->
            if (recyclerVier is RecyclerView) {
                callback(recyclerVier)
            }
        }
    }

    private fun setCurrentItem(position: Int, smoothScroll: Boolean = true) {
        viewPager2.setCurrentItem(position, smoothScroll)
    }

    override fun createContentPanel(): View {
        return viewPager2.also {
            buildContentPanel(it)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun reloadData() {
        log.i("reloadData")
        val mediaVisibility = findVisibility()
        val gallery = MediaStore.loadGallery(this, mediaVisibility, MediaType.Video)
        gallery.load { gallery, success ->
            mediaData.clear()
            mediaData.addAll(gallery.fileList)
            videoManager.resetMediaList(gallery.fileList, currentPosition)
            videoAdapter.notifyDataSetChanged()
            mediaFlowStoreView.resetData(mediaData)
            setCurrentItem(currentPosition, false)
            log.i("reloadData end, isSuccess=$success, mediaCount=${mediaData.size}, index=$currentPosition")
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mediaFlowStoreView.updateSpanCount(this)
    }

    override fun onDrawerChanged(isOpen: Boolean) {
        if (isOpen) {
            videoManager.pause()
        }
    }

    override fun createDrawerPanel(): View {
        return mediaFlowStoreView.getView(this)
    }

    private fun buildContentPanel(viewPager2: ViewPager2) {
        viewPager2.adapter = videoAdapter
        viewPager2.orientation = ViewPager2.ORIENTATION_VERTICAL
        viewPager2.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentPosition = position
                onSelected(position)
            }
        })
    }

    private fun changeDecoration(isVisibility: Boolean) {
        if (isVisibility) {
            showDecorationPanel()
        } else {
            hideDecorationPanel()
        }
    }

    private fun onSelected(position: Int) {
        log.i("onSelected: $position")
        optRecyclerView { recyclerVier ->
            val holder = recyclerVier.findViewHolderForAdapterPosition(position)
            if (holder is VideoHolder) {
                onFocusChanged(holder, position)
            } else {
                recyclerVier.post {
                    log.i("onSelected: $position, holder is null, try again")
                    onSelected(position)
                }
            }
        }
    }

    private fun onFocusChanged(holder: VideoHolder, position: Int) {
        log.i("onFocusChanged: $position")
        lastHolder?.let { old ->
            old.videoController = null
            old.videoPlayerView.player = null
            old.changeDecorationCallback = null
        }

        videoManager.changeView(lastHolder?.videoPlayerView, holder.videoPlayerView)

        holder.videoController = videoManager
        holder.changeDecorationCallback = ::changeDecoration
        videoManager.eventObserver.setFocus(holder.videoListener)

        lastHolder = holder
        videoManager.play(position)
    }

    override fun onWindowInsetsChanged(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        videoAdapter.setInsets(left, top, right, bottom)
        mediaFlowStoreView.onInsetsChanged(left, top, right, bottom)
    }

    private class PlayAdapter(
        private val videoList: List<MediaInfo.File>
    ) : RecyclerView.Adapter<VideoHolder>() {

        private var layoutInflater: LayoutInflater? = null

        private var insets = Rect()

        @SuppressLint("NotifyDataSetChanged")
        fun setInsets(left: Int, top: Int, right: Int, bottom: Int) {
            if (left != insets.left || top != insets.top || right != insets.right || bottom != insets.bottom) {
                insets.set(left, top, right, bottom)
                notifyDataSetChanged()
            }
        }

        private fun getLayoutInflater(parent: ViewGroup): LayoutInflater {
            return layoutInflater ?: LayoutInflater.from(parent.context).also {
                layoutInflater = it
            }
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): VideoHolder {
            return VideoHolder(
                PageVideoFlowBinding.inflate(getLayoutInflater(parent), parent, false)
            )
        }

        override fun onBindViewHolder(
            holder: VideoHolder,
            position: Int
        ) {
            holder.onBind(videoList[position])
            holder.onInsetsChanged(insets.left, insets.top, insets.right, insets.bottom)
        }

        override fun getItemCount(): Int {
            return videoList.size
        }

    }

    private class VideoHolder(
        private val binding: PageVideoFlowBinding
    ) : RecyclerView.ViewHolder(binding.root) {

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
            updateControlVisibility(false)
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

    }

    private class ClickHelper(
        private val keepTimeMs: Long = 300,
        private val onClick: (Int) -> Unit
    ) : View.OnClickListener {
        private var lastClickTime: Long = 0
        private var clickCount = 0

        private val invokeTask = task {
            onClick.invoke(clickCount)
        }

        fun reset() {
            clickCount = 0
            lastClickTime = 0
        }

        override fun onClick(v: View?) {
            val currentTime = System.currentTimeMillis()
            invokeTask.cancel()
            if ((currentTime - lastClickTime) < keepTimeMs) {
                clickCount++
            } else {
                clickCount = 1
            }
            lastClickTime = currentTime
            invokeTask.delay(keepTimeMs)
        }

    }

    private enum class VideoState {
        Pending,
        Playing,
        Paused,
        Ended,
    }

}