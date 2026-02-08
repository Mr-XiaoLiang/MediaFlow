package com.lollipop.mediaflow.page

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MediaStore
import com.lollipop.mediaflow.data.MediaType
import com.lollipop.mediaflow.data.MediaVisibility
import com.lollipop.mediaflow.page.flow.VideoPlayHolder
import com.lollipop.mediaflow.tools.MediaPageHelper
import com.lollipop.mediaflow.ui.BasicGalleryActivity
import com.lollipop.mediaflow.video.VideoManager

class VideoGalleryActivity : BasicGalleryActivity() {

    companion object {

        fun start(context: Context, mediaVisibility: MediaVisibility, position: Int) {
            MediaPageHelper.start(
                context,
                mediaVisibility,
                position,
                VideoGalleryActivity::class.java
            )
        }
    }

    private val videoHolder by lazy {
        VideoPlayHolder.create(layoutInflater)
    }
    private val videoManager by lazy {
        VideoManager(this)
    }
    private var currentPosition = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentPosition = MediaPageHelper.getMediaPosition(this)
        reloadData()
    }

    override fun createContentPanel(): View {
        videoManager.changeView(null, videoHolder.videoPlayerView)
        videoHolder.videoController = videoManager
        videoHolder.changeDecorationCallback = ::changeDecoration
        videoManager.eventObserver.setFocus(videoHolder.videoListener)
        return videoHolder.itemView
    }

    override fun onMediaClick(
        mediaInfo: MediaInfo.File,
        position: Int
    ) {
        onSelected(mediaInfo, position)
    }

    private fun changeDecoration(isVisibility: Boolean) {
        if (isVisibility) {
            showDecorationPanel()
        } else {
            hideDecorationPanel()
        }
    }

    override fun onGuidelineInsetsChanged(left: Int, top: Int, right: Int, bottom: Int) {
        super.onGuidelineInsetsChanged(left, top, right, bottom)
        when (currentOrientation) {
            Orientation.PORTRAIT -> {
                videoHolder.onInsetsChanged(left, top, right, 0)
            }

            Orientation.LANDSCAPE -> {
                videoHolder.onInsetsChanged(left, top, 0, bottom)
            }
        }
    }

    private fun onSelected(mediaInfo: MediaInfo.File?, position: Int) {
        onSelected(mediaInfo)
        if (mediaInfo != null) {
            videoHolder.onBind(mediaInfo)
        }
        if (position < 0) {
            videoManager.pause()
        } else {
            videoManager.play(position)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun reloadData() {
        log.i("reloadData")
        val mediaVisibility = MediaPageHelper.getMediaVisibility(this)
        val gallery = MediaStore.loadGallery(this, mediaVisibility, MediaType.Video)
        gallery.load { gallery, success ->
            val list = gallery.fileList
            onGalleryDataChanged(list)
            videoManager.resetMediaList(gallery.fileList, currentPosition)
            onSelected(list.getOrNull(currentPosition), currentPosition)
            log.i("reloadData end, isSuccess=$success, mediaCount=${list.size}, index=$currentPosition")
        }
    }

}