package com.lollipop.mediaflow.page.play

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MediaStore
import com.lollipop.mediaflow.data.MediaType
import com.lollipop.mediaflow.tools.MediaPlayLauncher
import com.lollipop.mediaflow.ui.BasicGalleryActivity

class PhotoGalleryActivity : BasicGalleryActivity() {

    private val photoView by lazy {
        SubsamplingScaleImageView(this)
    }

    private val mediaParams = MediaPlayLauncher.params()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaParams.onCreate(this, savedInstanceState)
        reloadData()
    }

    override fun createContentPanel(): View {
        return photoView.also {
            it.setBackgroundColor(Color.BLACK)
            it.setOnClickListener {
                changeDecoration(!isDecorationShown)
            }
        }
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
        mediaParams.onSelected(this, position)
        onSelected(mediaInfo)
        photoView.setImage(
            if (mediaInfo != null) {
                ImageSource.uri(mediaInfo.uri)
            } else {
                ImageSource.resource(R.mipmap.ic_launcher)
            }
        )
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun reloadData() {
        log.i("reloadData")
        val mediaVisibility = mediaParams.visibility
        val gallery = MediaStore.loadGallery(this, mediaVisibility, MediaType.Image)
        gallery.load { gallery, success ->
            val list = gallery.fileList
            onGalleryDataChanged(list)
            val currentPosition = mediaParams.currentPosition
            onSelected(list.getOrNull(currentPosition), currentPosition)
            log.i("reloadData end, isSuccess=$success, mediaCount=${list.size}, index=$currentPosition")
        }
    }

}