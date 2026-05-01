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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.LinkedList
import kotlin.text.ifEmpty

object MediaLoader {

    private val log by lazy {
        registerLog()
    }

    private var mediaDatabase: MediaDatabase? = null

    fun getMediaDatabase(context: Context): MediaDatabase {
        return mediaDatabase ?: MediaDatabase(context).also {
            mediaDatabase = it
            // 填充缓存
            it.fillingMetadataCache()
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
                        val orientation = exif.getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        )
                        val rotation = when (orientation) {
                            ExifInterface.ORIENTATION_NORMAL -> 0
                            ExifInterface.ORIENTATION_ROTATE_90 -> 90
                            ExifInterface.ORIENTATION_ROTATE_180 -> 180
                            ExifInterface.ORIENTATION_ROTATE_270 -> 270
                            // 包含镜像翻转的情况（虽然相机照片较少见，但建议处理以增强鲁棒性）
                            ExifInterface.ORIENTATION_TRANSPOSE -> 90
                            ExifInterface.ORIENTATION_TRANSVERSE -> 270
                            ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
                            // 其他情况（如 ORIENTATION_NORMAL 或 ORIENTATION_FLIP_HORIZONTAL）角度均为 0
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
                        getMediaDatabase(context).updateMediaMetadata(metadata)
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
                    getMediaDatabase(context).updateMediaMetadata(metadata)
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

    suspend fun loadTreeSync(context: Context, treeUri: Uri, path: String): MediaRoot {
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

    private suspend fun loadDirectorySync(
        context: Context,
        treeUri: Uri,
        path: String,
        parentDocId: String = ""
    ): MutableList<MediaInfo> {
        val result = mutableListOf<MediaInfo>()
        val subtitleList = mutableListOf<SubtitleFile>()
        val videoMap = mutableMapOf<String, MediaInfo.File>()
        try {
            loadDirectorySync(
                context = context,
                treeUri = treeUri,
                parentDocId = parentDocId
            ) { cursorLine ->
                val info = cursorLine.toMediaInfo(path = path)
                if (info != null) {
                    result.add(info)
                    if (info is MediaInfo.File && info.mediaType == MediaType.Video) {
                        val file = java.io.File(info.name)
                        videoMap[file.nameWithoutExtension] = info
                    }
                } else {
                    val subtitleInfo = cursorLine.toSubtitleInfo()
                    if (subtitleInfo != null) {
                        subtitleList.add(subtitleInfo)
                    }
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
            subtitleList.forEach { subtitle ->
                val baseName = subtitle.baseName
                videoMap[baseName]?.let { file ->
                    subtitle.videoId = file.docId
                    file.subtitleList.add(subtitle)
                }
            }
        } catch (e: Throwable) {
            log.e("loadDirectorySync", e)
        }
        log.d("loadDirectorySync path: $path result: ${result.size}")
        return result
    }

    fun parseToMediaInfo(
        cursorLine: CursorLine,
        path: String = "",
    ): MediaInfo? {
        return cursorLine.toMediaInfo(path = path)
    }

    private fun CursorLine.toMediaInfo(
        path: String,
    ): MediaInfo? {
        val cursorLine = this
        if (DocumentsContract.Document.MIME_TYPE_DIR == cursorLine.mimeType) {
            return MediaInfo.Directory(
                uri = cursorLine.fileUri,
                parentDocId = cursorLine.parentDocumentId,
                name = cursorLine.displayName,
                path = path,
                size = cursorLine.size,
                mimeType = cursorLine.mimeType,
                lastModified = cursorLine.lastModified,
                rootUri = cursorLine.treeUri,
                docId = cursorLine.documentId
            )
        }
        val mediaType = findMediaType(cursorLine.mimeType)
        if (mediaType != null) {
            return MediaInfo.File(
                uri = cursorLine.fileUri,
                parentDocId = cursorLine.parentDocumentId,
                name = cursorLine.displayName,
                path = path,
                size = cursorLine.size,
                mimeType = cursorLine.mimeType,
                lastModified = cursorLine.lastModified,
                rootUri = cursorLine.treeUri,
                mediaType = mediaType,
                docId = cursorLine.documentId
            )
        }
        return null
    }

    private fun CursorLine.toSubtitleInfo(): SubtitleFile? {
        val cursorLine = this
        val name = cursorLine.displayName
        val uri = cursorLine.fileUri
        val rootUri = cursorLine.treeUri
        val docId = cursorLine.documentId
        return SubtitleFile.parse(uri = uri, name, rootUri = rootUri, docId = docId)
    }

    suspend fun loadMediaFileSync(context: Context, uri: Uri): MediaInfo.File? {
        log.d("loadMediaFileSync uri = $uri")
        return withContext(Dispatchers.IO) {
            try {
                // 仅查询你需要的字段以提升性能
                val projection = arrayOf(
                    Column.DisplayName.key,
                    Column.MimeType.key,
                    Column.Size.key,
                )
                val uriString = uri.toString()
                val cursorLine = CursorLine(
                    treeUri = Uri.EMPTY,
                    parentDocumentId = uriString
                )
                cursorLine.fileUri = uri
                context.contentResolver.query(
                    uri, projection, null, null, null
                )?.use { cursor ->
                    if (cursor.moveToNext()) {
                        cursorLine.documentId = uriString
                        cursorLine.displayName = cursor.optString(Column.DisplayName)
                        cursorLine.mimeType = cursor.optString(Column.MimeType)
                        cursorLine.size = cursor.optLong(Column.Size)
                    }
                }
                val mediaType = findMediaType(cursorLine.mimeType) ?: return@withContext null
                return@withContext MediaInfo.File(
                    uri = cursorLine.fileUri,
                    parentDocId = "",
                    name = cursorLine.displayName,
                    path = uriString,
                    size = cursorLine.size,
                    mimeType = cursorLine.mimeType,
                    lastModified = cursorLine.lastModified,
                    rootUri = cursorLine.treeUri,
                    mediaType = mediaType,
                    docId = cursorLine.documentId
                )
            } catch (e: Throwable) {
                log.e("loadMediaFileSync", e)
            }
            return@withContext null
        }
    }

    suspend fun loadDirectorySync(
        context: Context,
        treeUri: Uri,
        parentDocId: String = "",
        callback: suspend (CursorLine) -> Unit
    ) {
        log.d("loadDirectorySync treeUri = $treeUri, parentDocId = $parentDocId")
        try {
            val parentDocumentId = parentDocId.ifEmpty {
                DocumentsContract.getTreeDocumentId(treeUri)
            }
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                parentDocumentId
            )

            // 仅查询你需要的字段以提升性能
            val projection = arrayOf(
                Column.DocumentId.key,
                Column.DisplayName.key,
                Column.MimeType.key,
                Column.Size.key,
                Column.LastModified.key
            )
            val cursorLine = CursorLine(
                treeUri = treeUri,
                parentDocumentId = parentDocumentId
            )
            context.contentResolver.query(
                childrenUri, projection, null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.optString(Column.DocumentId)
                    cursorLine.documentId = docId
                    cursorLine.displayName = cursor.optString(Column.DisplayName)
                    cursorLine.mimeType = cursor.optString(Column.MimeType)
                    cursorLine.size = cursor.optLong(Column.Size)
                    cursorLine.lastModified = cursor.optLong(Column.LastModified)
                    cursorLine.fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    callback(cursorLine)
                }
            }
        } catch (e: Throwable) {
            log.e("loadDirectorySync", e)
        }
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

    class CursorLine(
        val treeUri: Uri,
        val parentDocumentId: String,
    ) {
        var documentId = ""
        var displayName = ""
        var mimeType = ""
        var size = 0L
        var lastModified = 0L
        var fileUri: Uri = Uri.EMPTY
    }

}