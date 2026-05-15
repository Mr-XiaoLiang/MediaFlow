package com.lollipop.auditory.audio

import android.app.Activity
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.lollipop.auditory.data.AudioInfo

object AudioServiceHelper {

    fun startForegroundService(activity: Activity, intent: Intent) {
        activity.startForegroundService(intent)
    }

    fun toMediaItem(info: AudioInfo): MediaItem {
        return MediaItem.Builder()
            .setMediaId("local_id")
            .setUri(info.uri) // 本地音频文件的 Uri
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(info.displayName)
                    .setArtist(info.artist)
                    .setAlbumTitle(info.album)
                    // 如果你有本地解析出来的封面图片 Bitmap，可以直接转成字节数组传给系统
                    // .setArtworkData(artworkByteArray, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    .build()
            )
            .build()
    }

}