package com.lollipop.mediaflow.page

import android.annotation.SuppressLint
import android.content.Context
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
import com.lollipop.mediaflow.data.MediaVisibility
import com.lollipop.mediaflow.page.flow.MediaFlowStoreView
import com.lollipop.mediaflow.page.flow.VideoPlayHolder
import com.lollipop.mediaflow.tools.MediaPageHelper
import com.lollipop.mediaflow.ui.BasicFlowActivity
import com.lollipop.mediaflow.video.VideoManager

class VideoFlowActivity : BasicFlowActivity() {

    companion object {

        fun start(context: Context, mediaVisibility: MediaVisibility, position: Int) {
            MediaPageHelper.start(context, mediaVisibility, position, VideoFlowActivity::class.java)
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

    private var lastHolder: VideoPlayHolder? = null

    private var currentPosition = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentPosition = MediaPageHelper.getMediaPosition(this)
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
        val mediaVisibility = MediaPageHelper.getMediaVisibility(this)
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
            if (holder is VideoPlayHolder) {
                onFocusChanged(holder, position)
            } else {
                recyclerVier.post {
                    log.i("onSelected: $position, holder is null, try again")
                    onSelected(position)
                }
            }
        }
    }

    private fun onFocusChanged(holder: VideoPlayHolder, position: Int) {
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