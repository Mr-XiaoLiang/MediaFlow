package com.lollipop.mediaflow.page.play

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isEmpty
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MediaStore
import com.lollipop.mediaflow.data.MediaType
import com.lollipop.mediaflow.data.MetadataLoader
import com.lollipop.mediaflow.page.flow.MediaFlowStoreView
import com.lollipop.mediaflow.page.flow.VideoPlayHolder
import com.lollipop.mediaflow.tools.ArchiveHelper
import com.lollipop.mediaflow.tools.MediaPlayLauncher
import com.lollipop.mediaflow.tools.safeRun
import com.lollipop.mediaflow.ui.BasicFlowActivity
import com.lollipop.mediaflow.video.VideoManager
import kotlin.math.max

class VideoFlowActivity : BasicFlowActivity(), VideoPlayHolder.VideoTouchDisplay,
    VideoPlayHolder.DecorationVisibilityCallback {

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

    private var lastHolder: VideoPlayHolder? = null

    private val mediaParams = MediaPlayLauncher.params()

    private var gallery: MediaStore.Gallery? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaParams.onCreate(this, savedInstanceState)
        setAppearanceLightStatusBars(false)
        reloadData()
    }

    private fun onItemClick(position: Int) {
        setCurrentItem(position, false)
    }

    override fun onSideItemClick(mediaInfo: MediaInfo.File, position: Int) {
        setCurrentItem(position, false)
    }

    private fun optRecyclerView(callback: (RecyclerView) -> Unit) {
        safeRun {
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
    }

    private fun currentPosition(): Int {
        return viewPager2.currentItem
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
        val mediaVisibility = mediaParams.visibility
        var mediaGallery = gallery
        if (mediaGallery == null) {
            mediaGallery = MediaStore.loadGallery(this, mediaVisibility, MediaType.Video)
            gallery = mediaGallery
        }
        mediaGallery.loadChoose { gallery, success ->
            mediaData.clear()
            mediaData.addAll(gallery.fileList)
            val currentPosition = mediaParams.currentPosition
            updateSideMediaData(mediaData)
            videoManager.resetMediaList(gallery.fileList, currentPosition)
            videoAdapter.notifyDataSetChanged()
            mediaFlowStoreView.resetData(mediaData)
            setCurrentItem(currentPosition, false)
            log.i("reloadData end, isSuccess=$success, mediaCount=${mediaData.size}, index=$currentPosition")
        }
    }

    override fun onOrientationChanged(orientation: Orientation) {
        super.onOrientationChanged(orientation)
        lastHolder?.resetScaleGesture()
    }

    private fun optCurrentHolder(callback: (VideoPlayHolder) -> Unit) {
        optHolderHolder(currentPosition(), callback)
    }

    private fun optHolderHolder(position: Int, callback: (VideoPlayHolder) -> Unit) {
        optRecyclerView { recyclerVier ->
            val adapter = recyclerVier.adapter
            if (adapter != null && adapter.itemCount > position) {
                val holder = recyclerVier.findViewHolderForAdapterPosition(position)
                if (holder is VideoPlayHolder) {
                    callback(holder)
                } else {
                    recyclerVier.post {
                        optHolderHolder(position, callback)
                    }
                }
            }
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
                onSelected(position)
            }
        })
    }

    private fun onSelected(position: Int) {
        log.i("onSelected: $position")
        mediaParams.onSelected(this, position)
        if (position < 0 || position >= mediaData.size) {
            updateTitle(titleValue = "", size = "", format = "", duration = "")
        } else {
            val file = mediaData[position]
            onSideSelected(file, position)
            val job = MetadataLoader.load(this, file) {
                updateTitle(
                    file.name,
                    size = it?.sizeFormat ?: "",
                    format = file.suffix.uppercase(),
                    duration = it?.durationFormat ?: ""
                )
            }
            if (job != null) {
                updateTitle(file.name, size = "", format = "", duration = "")
            }
        }

        optHolderHolder(position) { holder ->
            onFocusChanged(holder, position)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        log.i("onSaveInstanceState")
        mediaParams.onSaveInstanceState(this, outState)
    }

    override fun changeDecorationVisibility(isShow: Boolean) {
        changeDecoration(isShow)
    }

    private fun onFocusChanged(holder: VideoPlayHolder, position: Int) {
        log.i("onFocusChanged: $position")
        lastHolder?.onFocusChange(controller = null, touchDisplay = null, decorationCallback = null)

        videoManager.changeView(lastHolder?.videoPlayerView, holder.videoPlayerView)

        holder.onFocusChange(
            controller = videoManager,
            touchDisplay = this,
            decorationCallback = this
        )
        videoManager.eventObserver.setFocus(holder.videoListener)
        holder.onSelected(isDecorationShown)
        lastHolder = holder
        videoManager.play(position)
    }

    override fun onWindowInsetsChanged(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        super.onWindowInsetsChanged(left, top, right, bottom)
        val isSideShown = isSidePanelShown()
        videoAdapter.setInsets(
            left,
            top,
            if (isSideShown) {
                0
            } else {
                right
            },
            bottom
        )
        mediaFlowStoreView.onInsetsChanged(left, top, right, bottom)
    }

    override fun startPlaybackSpeed() {
    }

    override fun stopPlaybackSpeed() {
    }

    override fun startSeekMode() {
    }

    override fun onTouchSeek(weight: Float, precision: Float) {
    }

    override fun stopSeekMode(weight: Float) {
    }

    override fun onArchiveClick(position: Int) {
        videoManager.pause()
        val file = mediaData[position]
        mediaData.removeAt(position)
        videoAdapter.notifyItemRemoved(position)
        val maxIndex = mediaData.size - 1
        val newPosition = if (position <= maxIndex) {
            position
        } else {
            maxIndex
        }
        if (newPosition >= 0) {
            onSelected(newPosition)
        }
        videoManager.resetMediaList(mediaData, max(newPosition, 0))
        // 最后再去移除文件，避免引用丢失
        ArchiveHelper.remove(this, file, gallery)
    }

    private class PlayAdapter(
        private val videoList: List<MediaInfo.File>
    ) : RecyclerView.Adapter<VideoPlayHolder>() {

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
        ): VideoPlayHolder {
            return VideoPlayHolder.create(getLayoutInflater(parent), parent)
        }

        override fun onBindViewHolder(
            holder: VideoPlayHolder,
            position: Int
        ) {
            holder.onBind(videoList[position])
            holder.onInsetsChanged(insets.left, insets.top, insets.right, insets.bottom)
        }

        override fun getItemCount(): Int {
            return videoList.size
        }

    }

}