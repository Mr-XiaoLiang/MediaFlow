package com.lollipop.mediaflow.page.play

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MediaStore
import com.lollipop.mediaflow.data.MediaType
import com.lollipop.mediaflow.page.flow.VideoPlayHolder
import com.lollipop.mediaflow.tools.MediaPlayLauncher
import com.lollipop.mediaflow.ui.BasicGalleryActivity
import com.lollipop.mediaflow.video.VideoManager

class VideoGalleryActivity : BasicGalleryActivity() {

    private val videoHolder by lazy {
        VideoPlayHolder.create(layoutInflater)
    }
    private val videoManager by lazy {
        VideoManager(this)
    }
    private var mediaParams = MediaPlayLauncher.params()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaParams.onCreate(this, savedInstanceState)
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
        mediaParams.onSelected(this, position)
        onSelected(mediaInfo)
        if (mediaInfo != null) {
            videoHolder.onBind(mediaInfo)
            videoHolder.onSelected(isDecorationShown)
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
        val mediaVisibility = mediaParams.visibility
        val gallery = MediaStore.loadGallery(this, mediaVisibility, MediaType.Video)
        gallery.load { gallery, success ->
            val list = gallery.fileList
            onGalleryDataChanged(list)
            val currentPosition = mediaParams.currentPosition
            videoManager.resetMediaList(gallery.fileList, currentPosition)
            onSelected(list.getOrNull(currentPosition), currentPosition)
            log.i("reloadData end, isSuccess=$success, mediaCount=${list.size}, index=$currentPosition")
        }
    }

}