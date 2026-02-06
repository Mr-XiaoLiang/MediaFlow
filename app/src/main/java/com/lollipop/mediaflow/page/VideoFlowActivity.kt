package com.lollipop.mediaflow.page

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.isVisible
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MediaStore
import com.lollipop.mediaflow.data.MediaType
import com.lollipop.mediaflow.data.MediaVisibility
import com.lollipop.mediaflow.databinding.PageVideoFlowBinding
import com.lollipop.mediaflow.ui.BasicFlowActivity
import com.lollipop.mediaflow.video.VideoController
import com.lollipop.mediaflow.video.VideoListener
import com.lollipop.mediaflow.video.VideoManager

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

    private val mediaData = mutableListOf<MediaInfo.File>()
    private val videoAdapter = PlayAdapter(mediaData)

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

    private fun reloadData() {
        log.i("reloadData")
        val mediaVisibility = findVisibility()
        val gallery = MediaStore.loadGallery(this, mediaVisibility, MediaType.Video)
        gallery.load { gallery, success ->
            mediaData.clear()
            mediaData.addAll(gallery.fileList)
            videoManager.resetMediaList(gallery.fileList, currentPosition)
            videoAdapter.notifyDataSetChanged()
            setCurrentItem(currentPosition, false)
            log.i("reloadData end, isSuccess=$success, mediaCount=${mediaData.size}, index=$currentPosition")
        }
    }

    override fun onOrientationChanged(orientation: Orientation) {
        when (orientation) {
            Orientation.PORTRAIT -> {
                showSystemUI()
            }

            Orientation.LANDSCAPE -> {
                hideSystemUI()
            }
        }
    }

    override fun onDrawerChanged(isOpen: Boolean) {
        if (isOpen) {
            videoManager.pause()
        }
    }

    override fun createDrawerPanel(): View {
        return View(this).apply {
            background = ColorDrawable(Color.Red.toArgb())
        }
    }

    override fun buildContentPanel(viewPager2: ViewPager2) {
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

    private fun onSelected(position: Int) {
        optRecyclerView { recyclerVier ->
            recyclerVier.findViewHolderForAdapterPosition(position)?.let {
                if (it is VideoHolder) {
                    onFocusChanged(it, position)
                }
            }
        }
    }

    private fun onFocusChanged(holder: VideoHolder, position: Int) {
        lastHolder?.let { old ->
            old.videoController = null
            old.videoPlayerView.player = null
        }

        videoManager.changeView(lastHolder?.videoPlayerView, holder.videoPlayerView)

        holder.videoController = videoManager
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

        private val clickHelper = ClickHelper(onClick = ::onClick)

        private var videoLength: Long = 0
        private var videoProgress: Long = 0
        private var videoState = VideoState.Pending

        val videoListener = object : VideoListener {
            override fun onVideoBegin(length: Long) {
                videoState = VideoState.Playing
                binding.artworkView.isVisible = false
                videoLength = length
                binding.progressSlider.valueFrom = 0F
                binding.progressSlider.valueTo = length.toFloat()
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
                videoState = VideoState.Paused
                binding.playButton.isVisible = true
            }

            override fun onVideoEnd() {
                videoState = VideoState.Ended
            }

            override fun onPlayerError(msg: String) {
                Toast.makeText(itemView.context, msg, Toast.LENGTH_SHORT).show()
            }
        }

        var videoController: VideoController? = null

        val videoPlayerView: PlayerView
            get() {
                return binding.playerView
            }

        private var isControlVisibility = false

        init {
            binding.root.setOnClickListener(clickHelper)
        }

        @SuppressLint("SetTextI18n")
        private fun updateProgress(ms: Long) {
            // 每100毫秒更新一次进度
            if (videoProgress / 100 != ms / 100) {
                videoProgress = ms
                binding.progressSlider.value = ms.toFloat()
                binding.progressTextView.text = "${formatTime(ms)} / ${formatTime(videoLength)}"
            }
        }

        private fun formatTime(ms: Long): String {
            val minutes = ms / 60000
            val seconds = (ms / 1000) % 60
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
            updateControlVisibility(false)
        }

        private fun updateControlVisibility(visible: Boolean) {
            binding.controlLayout.isVisible = visible
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

        fun reset() {
            clickCount = 0
            lastClickTime = 0
        }

        override fun onClick(v: View?) {
            val currentTime = System.currentTimeMillis()
            if ((currentTime - lastClickTime) < keepTimeMs) {
                clickCount++
            } else {
                clickCount = 1
            }
            lastClickTime = currentTime
            onClick.invoke(clickCount)
        }

    }

    private enum class VideoState {
        Pending,
        Playing,
        Paused,
        Ended,
    }

}