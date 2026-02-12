package com.lollipop.mediaflow.data

import android.content.Context
import android.net.Uri

class MediaRoot(
    val name: String,
    val children: List<MediaInfo>
)

sealed class MediaDirectoryTree {

    val children = mutableListOf<MediaDirectoryTree>()
    val fileCount: Int
        get() {
            return videoCount + imageCount
        }

    var videoCount: Int = 0
        protected set

    var imageCount: Int = 0
        protected set

    abstract val id: String
    abstract val name: String

    /**
     * 计算当前目录下的文件数量
     * @return 一个数组，第一个元素为视频数量，第二个元素为图片数量
     */
    abstract fun calculateFileCount(): IntArray

    class Root(val current: MediaRoot) : MediaDirectoryTree() {
        override fun calculateFileCount(): IntArray {
            var video = 0
            var image = 0
            current.children.forEach {
                if (it is MediaInfo.File) {
                    if (it.mediaType == MediaType.Video) {
                        video++
                    } else if (it.mediaType == MediaType.Image) {
                        image++
                    }
                }
            }
            for (child in children) {
                val childCount = child.calculateFileCount()
                video += childCount[0]
                image += childCount[1]
            }
            videoCount = video
            imageCount = image
            return intArrayOf(video, image)
        }

        override val id: String by lazy {
            "Root_${current.name}"
        }
        override val name: String by lazy {
            current.name
        }
    }

    class Directory(
        val current: MediaInfo.Directory,
        val parent: MediaDirectoryTree
    ) : MediaDirectoryTree() {
        override fun calculateFileCount(): IntArray {
            var video = 0
            var image = 0
            current.children.forEach {
                if (it is MediaInfo.File) {
                    if (it.mediaType == MediaType.Video) {
                        video++
                    } else if (it.mediaType == MediaType.Image) {
                        image++
                    }
                }
            }
            for (child in children) {
                val childCount = child.calculateFileCount()
                video += childCount[0]
                image += childCount[1]
            }
            videoCount = video
            imageCount = image
            return intArrayOf(video, image)
        }

        override val id: String by lazy {
            current.uriString
        }
        override val name: String by lazy {
            current.name
        }
    }
}

sealed class MediaInfo(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val size: Long,
    val lastModified: Long,
    val path: String,
    val parentDocId: String,
    val rootUri: Uri,
    val docId: String
) {

    val uriString: String by lazy {
        uri.toString()
    }

    val rootUriString: String by lazy {
        rootUri.toString()
    }

    class Directory(
        uri: Uri,
        name: String,
        mimeType: String,
        size: Long,
        lastModified: Long,
        path: String,
        parentDocId: String,
        rootUri: Uri,
        docId: String
    ) : MediaInfo(
        uri = uri,
        name = name,
        mimeType = mimeType,
        size = size,
        lastModified = lastModified,
        path = path,
        parentDocId = parentDocId,
        rootUri = rootUri,
        docId = docId
    ) {
        val children = mutableListOf<MediaInfo>()
    }

    class File(
        uri: Uri,
        name: String,
        mimeType: String,
        size: Long,
        lastModified: Long,
        path: String,
        parentDocId: String,
        rootUri: Uri,
        docId: String,
        val mediaType: MediaType
    ) : MediaInfo(
        uri = uri,
        name = name,
        mimeType = mimeType,
        size = size,
        lastModified = lastModified,
        path = path,
        parentDocId = parentDocId,
        rootUri = rootUri,
        docId = docId
    ) {

        var metadata: MediaMetadata? = null

        fun loadMetadataSync(context: Context, cacheOnly: Boolean = true) {
            if (metadata == null) {
                MediaLoader.loadMediaMetadataSync(context, this, cacheOnly)
            }
        }

    }

}