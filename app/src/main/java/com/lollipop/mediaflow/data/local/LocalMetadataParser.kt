package com.lollipop.mediaflow.data.local

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.exifinterface.media.ExifInterface
import com.lollipop.common.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.data.MediaMetadata

/**
 * Local 元数据解析：负责从 ContentProvider 文件读取 EXIF / 视频元数据，
 * 并配合 [MediaDatabase] 做缓存读写。
 * 从原 MediaLoader 抽取，作为 Local 专属能力。
 */
object LocalMetadataParser {

    private val log by lazy {
        registerLog()
    }

    fun loadMediaMetadataSync(
        context: Context,
        file: MediaInfo.File,
        cacheOnly: Boolean = true
    ) {
        if (file.metadata == null) {
            loadMediaMetadataLocalSync(context, file)
            if (file.metadata == null && !cacheOnly) {
                loadMediaMetadataRemoteSync(context, file)
            }
        }
    }

    private fun loadMediaMetadataLocalSync(
        context: Context,
        file: MediaInfo.File
    ) {
        val docId = file.docId
        val database = LocalMediaLoader.getMediaDatabase(context)
        try {
            // 先查询数据库是否有缓存
            val cachedMetadata = database.findMediaMetadata(docId)
            if (cachedMetadata != null) {
                // 如果缓存的 lastModified 与文件的 lastModified 相同，直接返回缓存
                if (cachedMetadata.lastModified == file.lastModified) {
                    file.metadata = cachedMetadata
                }
            }
        } catch (e: Exception) {
            // 处理解析失败的情况
            log.e("loadMediaMetadataLocalSync", e)
        }
    }

    private fun loadMediaMetadataRemoteSync(
        context: Context,
        file: MediaInfo.File
    ) {
        when (file.mediaType) {
            MediaType.Image -> {
                try {
                    context.contentResolver.openFileDescriptor(file.uri, "r")?.use { pfd ->
                        val exif = ExifInterface(pfd.fileDescriptor)
                        val width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
                        val height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
                        val orientation = exif.getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        )
                        val rotation = when (orientation) {
                            ExifInterface.ORIENTATION_NORMAL -> 0
                            ExifInterface.ORIENTATION_ROTATE_90 -> 90
                            ExifInterface.ORIENTATION_ROTATE_180 -> 180
                            ExifInterface.ORIENTATION_ROTATE_270 -> 270
                            ExifInterface.ORIENTATION_TRANSPOSE -> 90
                            ExifInterface.ORIENTATION_TRANSVERSE -> 270
                            ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
                            else -> 0
                        }
                        val metadata = MediaMetadata.fromImage(
                            docId = file.docId,
                            width = width,
                            height = height,
                            rotation = rotation,
                            lastModified = file.lastModified,
                        )
                        file.metadata = metadata
                        LocalMediaLoader.getMediaDatabase(context).updateMediaMetadata(metadata)
                    }
                } catch (e: Throwable) {
                    log.e("loadMediaMetadataRemoteSync: ${file.uri}", e)
                }
            }

            MediaType.Video -> {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, file.uri)
                    val width =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    val height =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    val duration = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DURATION
                    )?.toLongOrNull() ?: 0
                    val metadata = MediaMetadata.fromVideo(
                        docId = file.docId,
                        width = width?.toIntOrNull() ?: 0,
                        height = height?.toIntOrNull() ?: 0,
                        duration = duration,
                        lastModified = file.lastModified,
                    )
                    file.metadata = metadata
                    LocalMediaLoader.getMediaDatabase(context).updateMediaMetadata(metadata)
                } catch (e: Throwable) {
                    log.e("loadMediaMetadataRemoteSync: ${file.uri}", e)
                } finally {
                    retriever.release()
                }
            }
        }
    }

}
