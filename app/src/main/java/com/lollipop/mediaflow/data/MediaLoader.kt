package com.lollipop.mediaflow.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import androidx.exifinterface.media.ExifInterface
import com.lollipop.mediaflow.tools.CursorColumn
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.optLong
import com.lollipop.mediaflow.tools.optString
import java.util.LinkedList

object MediaLoader {

    private val log by lazy {
        registerLog()
    }

    private var mediaDatabase: MediaDatabase? = null

    private fun getMediaDatabase(context: Context): MediaDatabase {
        return mediaDatabase ?: MediaDatabase(context).also {
            mediaDatabase = it
            // 填充缓存
            it.fillingCache()
        }
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
        val database = getMediaDatabase(context)
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
            log.e("loadMediaMetadataSync", e)
        }
    }

    fun getRootFolderName(context: Context, treeUri: Uri): String? {
        // 1. 从 treeUri 中提取该目录的 DocumentId
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)

        // 2. 构建该目录自身的 DocumentUri（注意：不是 buildChildDocumentsUri）
        val rootDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

        // 3. 查询 COLUMN_DISPLAY_NAME 字段
        return try {
            context.contentResolver.query(
                rootDocumentUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.optString(DocumentsContract.Document.COLUMN_DISPLAY_NAME) // 返回文件夹真实名称
                } else null
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            null
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
                        val rotation = exif.getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        )
                        val metadata = MediaMetadata.fromImage(
                            docId = file.docId,
                            width = width,
                            height = height,
                            rotation = rotation,
                            lastModified = file.lastModified,
                        )
                        file.metadata = metadata
                    }
                } catch (e: Throwable) {
                    log.e("loadMediaMetadataSync: ${file.uri}", e)
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
                    // 别忘了最后 release
                    val metadata = MediaMetadata.fromVideo(
                        docId = file.docId,
                        width = width?.toIntOrNull() ?: 0,
                        height = height?.toIntOrNull() ?: 0,
                        duration = duration,
                        lastModified = file.lastModified,
                    )
                    file.metadata = metadata
                } catch (e: Throwable) {
                    // 处理解析失败的情况
                    log.e("loadMediaMetadataSync: ${file.uri}", e)
                } finally {
                    retriever.release()
                }
            }
        }
    }

    fun expandFolderSync(list: List<MediaInfo>): List<MediaInfo.File> {
        val result = mutableListOf<MediaInfo.File>()
        val pendingList = LinkedList<MediaInfo.Directory>()
        list.forEach {
            if (it is MediaInfo.File) {
                result.add(it)
            }
            if (it is MediaInfo.Directory) {
                pendingList.add(it)
            }
        }
        while (pendingList.isNotEmpty()) {
            val directory = pendingList.removeFirst()
            directory.children.forEach {
                if (it is MediaInfo.File) {
                    result.add(it)
                }
                if (it is MediaInfo.Directory) {
                    pendingList.add(it)
                }
            }
        }
        return result
    }

    fun loadTreeSync(context: Context, treeUri: Uri, path: String): MediaRoot {
        log.i("loadTreeSync, start treeUri=$treeUri, path=$path")
        val startTime = System.currentTimeMillis()
        val result = loadDirectorySync(context = context, treeUri = treeUri, path, parentDocId = "")
        val pendingList = LinkedList<MediaInfo.Directory>()
        result.forEach {
            if (it is MediaInfo.Directory) {
                pendingList.add(it)
            }
        }
        while (pendingList.isNotEmpty()) {
            val directory = pendingList.removeFirst()
            val children = loadDirectorySync(
                context = context,
                treeUri = treeUri,
                path = "${directory.path}/${directory.name}",
                parentDocId = directory.docId
            )
            directory.children.addAll(children)
            children.forEach {
                if (it is MediaInfo.Directory) {
                    pendingList.add(it)
                }
            }
        }
        val endTime = System.currentTimeMillis()
        log.d("loadTreeSync result: ${result.size} cost: ${endTime - startTime}ms")
        return MediaRoot(
            name = path,
            children = result
        )
    }

    private fun loadDirectorySync(
        context: Context,
        treeUri: Uri,
        path: String,
        parentDocId: String = ""
    ): List<MediaInfo> {
        val result = mutableListOf<MediaInfo>()
        try {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                parentDocId.ifEmpty {
                    DocumentsContract.getTreeDocumentId(treeUri)
                }
            )

            // 仅查询你需要的字段以提升性能
            val projection = arrayOf(
                Column.DocumentId.key,
                Column.DisplayName.key,
                Column.MimeType.key,
                Column.Size.key,
                Column.LastModified.key
            )

            context.contentResolver.query(
                childrenUri, projection, null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.optString(Column.DocumentId)
                    val name = cursor.optString(Column.DisplayName)
                    val mimeType = cursor.optString(Column.MimeType)
                    val size = cursor.optLong(Column.Size)
                    val lastModified = cursor.optLong(Column.LastModified)
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    val info = if (DocumentsContract.Document.MIME_TYPE_DIR == mimeType) {
                        MediaInfo.Directory(
                            uri = fileUri,
                            parentDocId = parentDocId,
                            name = name,
                            path = path,
                            size = size,
                            mimeType = mimeType,
                            lastModified = lastModified,
                            rootUri = treeUri,
                            docId = docId
                        )
                    } else {
                        val mediaType = findMediaType(mimeType) ?: continue
                        MediaInfo.File(
                            uri = fileUri,
                            parentDocId = parentDocId,
                            name = name,
                            path = path,
                            size = size,
                            mimeType = mimeType,
                            lastModified = lastModified,
                            rootUri = treeUri,
                            mediaType = mediaType,
                            docId = docId
                        )
                    }
                    result.add(info)
                }
            }
            val missList = mutableListOf<MediaInfo.File>()
            val expandList = expandFolderSync(result)
            for (file in expandList) {
                loadMediaMetadataLocalSync(context, file)
                if (file.metadata == null) {
                    missList.add(file)
                }
            }
//            for (file in missList) {
//                loadMediaMetadataRemoteSync(context, file)
//            }
            getMediaDatabase(context).updateMediaMetadata(missList.mapNotNull { it.metadata })
        } catch (e: Throwable) {
            log.e("loadDirectorySync", e)
        }
        log.d("loadDirectorySync path: $path result: ${result.size}")
        return result
    }

    private fun findMediaType(mimeType: String): MediaType? {
        return when {
            mimeType.startsWith(MediaType.Image.mimePrefix) -> MediaType.Image
            mimeType.startsWith(MediaType.Video.mimePrefix) -> MediaType.Video
            else -> null
        }
    }

    enum class Column(
        override val key: String
    ) : CursorColumn {
        DocumentId(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
        DisplayName(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
        MimeType(DocumentsContract.Document.COLUMN_MIME_TYPE),
        Size(DocumentsContract.Document.COLUMN_SIZE),
        LastModified(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
    }

}