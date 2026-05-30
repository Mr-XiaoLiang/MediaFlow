package com.lollipop.mediaflow.ui

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MediaMetadata
import com.lollipop.mediaflow.data.MediaType

object CoverLoader {

    fun load(imageView: ImageView, mediaInfo: MediaInfo) {
        when (mediaInfo) {
            is MediaInfo.File -> {
                when (mediaInfo.mediaType) {
                    MediaType.Image -> {
                        Glide.with(imageView)
                            .asBitmap()
                            .load(mediaInfo.uri)
                            .optViewSize(imageView, mediaInfo.metadata)
                            .into(imageView)
                    }

                    MediaType.Video -> {
                        Glide.with(imageView)
                            .load(mediaInfo.uri)
                            .optViewSize(imageView, mediaInfo.metadata)
                            .into(imageView)
                    }
                }
            }

            is MediaInfo.Directory -> {
                Glide.with(imageView)
                    .load(mediaInfo.uri)
                    .optViewSize(imageView, null)
                    .into(imageView)
            }
        }
    }

    private fun RequestBuilder<*>.optViewSize(
        imageView: ImageView,
        mediaMetadata: MediaMetadata?
    ): RequestBuilder<*> {
        val viewWidth = imageView.width.coerceAtMost(1440)
        val viewHeight = imageView.height.coerceAtMost(3168)
        if (viewWidth > 0 && viewHeight > 0) {
            return this.override(viewWidth, viewHeight)
        }
        if (mediaMetadata != null) {
            return this.override(
                mediaMetadata.width.coerceAtMost(1440),
                mediaMetadata.height.coerceAtMost(3168)
            )
        }
        return this
    }

}