package com.lollipop.mediaflow.page

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MediaStore
import com.lollipop.mediaflow.data.MediaType
import com.lollipop.mediaflow.data.MediaVisibility
import com.lollipop.mediaflow.tools.MediaPageHelper
import com.lollipop.mediaflow.ui.BasicGalleryActivity

class PhotoGalleryActivity : BasicGalleryActivity() {

    companion object {

        fun start(context: Context, mediaVisibility: MediaVisibility, position: Int) {
            MediaPageHelper.start(
                context,
                mediaVisibility,
                position,
                PhotoGalleryActivity::class.java
            )
        }
    }

    private val photoView by lazy {
        SubsamplingScaleImageView(this)
    }

    private var currentPosition = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentPosition = MediaPageHelper.getMediaPosition(this)
        reloadData()
    }

    override fun createContentPanel(): View {
        return photoView
    }

    override fun onMediaClick(
        mediaInfo: MediaInfo.File,
        position: Int
    ) {
        onSelected(mediaInfo, position)
    }

    private fun onSelected(
        mediaInfo: MediaInfo.File?,
        position: Int
    ) {
        photoView.setImage(
            if (mediaInfo != null) {
                ImageSource.uri(mediaInfo.uri)
            } else {
                ImageSource.resource(R.mipmap.ic_launcher)
            }
        )
        onSelected(mediaInfo)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun reloadData() {
        log.i("reloadData")
        val mediaVisibility = MediaPageHelper.getMediaVisibility(this)
        val gallery = MediaStore.loadGallery(this, mediaVisibility, MediaType.Image)
        gallery.load { gallery, success ->
            val list = gallery.fileList
            onGalleryDataChanged(list)
            onSelected(list.getOrNull(currentPosition), currentPosition)
            log.i("reloadData end, isSuccess=$success, mediaCount=${list.size}, index=$currentPosition")
        }
    }

}