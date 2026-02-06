package com.lollipop.mediaflow.video

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import com.lollipop.mediaflow.data.MediaInfo
import kotlin.math.max
import kotlin.math.min

@OptIn(UnstableApi::class)
class VideoPreload(
    val preloadManager: DefaultPreloadManager
) {

    private val mediaList = mutableListOf<MediaItem>()

    private val preloadSet = mutableSetOf<MediaItem>()

    fun reset(list: List<MediaInfo.File>, startIndex: Int = 0) {
        mediaList.clear()
        for (file in list) {
            mediaList.add(createMediaItem(file))
        }
        setCurrentIndex(startIndex)
    }

    fun setCurrentIndex(index: Int) {
        if (mediaList.isEmpty()) {
            preloadSet.forEach {
                preloadManager.remove(it)
            }
            return
        }
        // 确保索引在有效范围内
        val startIndex = max(0, index - 1)
        val endIndex = min(index + 1, mediaList.size - 1)
        val activeIndices = startIndex..endIndex
        // 临时的预加载集合，用于移除不在活动范围内的预加载项
        val tempSet = mutableSetOf<MediaItem>()
        // 先记录所有当前的预加载项
        tempSet.addAll(preloadSet)
        // 循环检查需要加载的范围
        for (i in activeIndices) {
            val media = mediaList[i]
            // 如果当前项已经在预加载集合中，那么表示它有效，需要从临时集合中移除
            if (tempSet.contains(media)) {
                tempSet.remove(media)
                continue
            }
            // 否则，将其添加到预加载管理器中
            preloadManager.add(media, i)
            // 并且将它记录下来
            preloadSet.add(media)
        }
        // 最后，移除所有不在活动范围内的预加载项
        tempSet.forEach {
            preloadManager.remove(it)
        }
        // 最后，更新预加载集合，移除所有不在活动范围内的项
        preloadSet.removeAll(tempSet)

        // 最后，更新当前播放索引
        preloadManager.setCurrentPlayingIndex(index)
        // 最后，通知预加载管理器更新
        preloadManager.invalidate()
    }

    fun getSource(index: Int): MediaSource? {
        if (mediaList.isEmpty()) {
            return null
        }
        if (index !in mediaList.indices) {
            return null
        }
        return preloadManager.getMediaSource(mediaList[index])
    }

    private fun createMediaItem(file: MediaInfo.File): MediaItem {
        return MediaItem.fromUri(file.uri)
    }

}