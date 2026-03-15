package com.lollipop.mediaflow.page.play

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MediaStore
import com.lollipop.mediaflow.data.MediaType
import com.lollipop.mediaflow.page.flow.VideoPlayHolder
import com.lollipop.mediaflow.tools.ArchiveHelper
import com.lollipop.mediaflow.tools.MediaPlayLauncher
import com.lollipop.mediaflow.ui.BasicGalleryActivity
import com.lollipop.mediaflow.video.VideoManager
import kotlin.math.max

class VideoGalleryActivity : BasicGalleryActivity(), VideoPlayHolder.VideoTouchDisplay {

    private val videoHolder by lazy {
        VideoPlayHolder.create(layoutInflater)
    }
    private val videoManager by lazy {
        VideoManager(this)
    }
    private var mediaParams = MediaPlayLauncher.params()

    private var gallery: MediaStore.Gallery? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaParams.onCreate(this, savedInstanceState)
        reloadData()
    }

    override fun createContentPanel(): View {
        videoManager.changeView(null, videoHolder.videoPlayerView)
        videoHolder.videoController = videoManager
        videoHolder.videoTouchDisplay = this
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
        onGallerySelected(mediaInfo, position)
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
        var mediaGallery = gallery
        if (mediaGallery == null) {
            mediaGallery = MediaStore.loadGallery(this, mediaVisibility, MediaType.Video)
            gallery = mediaGallery
        }
        mediaGallery.loadChoose { gallery, success ->
            val list = gallery.fileList
            onGalleryDataChanged(list)
            val currentPosition = mediaParams.currentPosition
            videoManager.resetMediaList(gallery.fileList, currentPosition)
            onSelected(list.getOrNull(currentPosition), currentPosition)
            log.i("reloadData end, isSuccess=$success, mediaCount=${list.size}, index=$currentPosition")
        }
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
        // 先暂停
        videoManager.pause()

        // 找到真正的序号
        val currentPosition = currentPosition()
        // 本地列表中移除
        val removed = removeAt(currentPosition)
        // 计算新的最大范围
        val maxIndex = mediaData.size - 1
        // 新的序号
        val newPosition = if (currentPosition <= maxIndex) {
            currentPosition
        } else {
            maxIndex
        }
        // 重置缓存集合
        videoManager.resetMediaList(mediaData, max(newPosition, 0))
        // 如果移除成功，那么就移动过去
        if (newPosition >= 0) {
            val newFile = mediaData[newPosition]
            onSelected(newFile, newPosition)
        }

        // 如果删除成功，这时候再去移除文件记录
        if (removed != null) {
            ArchiveHelper.remove(this, removed, gallery)
        }
    }

}