package com.lollipop.mediaflow.tools

import android.content.Context
import com.lollipop.common.tools.doAsync
import com.lollipop.common.tools.onUI
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MediaLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VideoDuplicateFinder {

    fun findDuplicates(
        context: Context,
        videoFiles: List<MediaInfo.File>,
        callback: (List<Duplicate>) -> Unit
    ) {
        doAsync {
            val duplicates = findDuplicates(context, videoFiles)
            onUI {
                callback(duplicates)
            }
        }
    }

    suspend fun findDuplicates(
        context: Context,
        videoFiles: List<MediaInfo.File>
    ): List<Duplicate> {
        return withContext(Dispatchers.IO) {
            val videoMap = HashMap<String, MutableList<Duplicate>>()

            for (file in videoFiles) {
                MediaLoader.loadMediaMetadataSync(context, file, cacheOnly = false)
                val metadata = file.metadata ?: continue
                val sizeFormat = metadata.sizeFormat
                val duration = metadata.duration
                val newMedia = Media(file, sizeFormat, duration)
                val pendingList = videoMap[sizeFormat]
                if (pendingList == null) {
                    // 没有见过的分辨率，新建一个集合
                    val newList = ArrayList<Duplicate>()
                    videoMap[sizeFormat] = newList
                    val newDuplicate = Duplicate()
                    newDuplicate.list.add(Media(file, sizeFormat, duration))
                    newList.add(newDuplicate)
                    continue
                }
                duplicatePull(pendingList, newMedia)
            }

            val duplicates = ArrayList<Duplicate>()
            // 最终检查结果
            videoMap.values.forEach { list ->
                // 遍历里面的集合
                list.forEach {
                    // 如果集合大于1，则认为是重复的
                    if (it.size > 1) {
                        duplicates.add(it)
                    }
                }
            }
            return@withContext duplicates
        }
    }

    private fun duplicatePull(list: MutableList<Duplicate>, newMedia: Media) {
        for (duplicate in list) {
            for (media in duplicate.list) {
                // 如果这个集合里有时间差不超过1秒的，则认为是重复的
                if (media.isDurationSame(newMedia)) {
                    // 放进去就完事了
                    duplicate.list.add(newMedia)
                    return
                }
            }
        }
        // 如果没有，那么新建一个集合
        val newDuplicate = Duplicate()
        newDuplicate.list.add(newMedia)
        list.add(newDuplicate)
    }

    class Duplicate {

        val list = ArrayList<Media>()

        val size: Int
            get() {
                return list.size
            }

    }

    class Media(
        val file: MediaInfo.File,
        val size: String,
        val duration: Long,
    ) {

        val durationSeconds by lazy {
            ((duration + 500) / 1000).toInt()
        }

        fun isDurationSame(other: Media, offset: Long = 1000L): Boolean {
            return durationSeconds == other.durationSeconds
        }

    }

}