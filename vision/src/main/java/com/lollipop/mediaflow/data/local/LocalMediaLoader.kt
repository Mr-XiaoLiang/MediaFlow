package com.lollipop.mediaflow.data.local

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import com.lollipop.common.tools.CursorColumn
import com.lollipop.common.tools.LLog.Companion.registerLog
import com.lollipop.common.tools.optLong
import com.lollipop.common.tools.optString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.LinkedList

/**
 * Local 专属加载器（原 MediaLoader）。
 * 负责本地 ContentProvider 文件的目录树读取、单文件加载、以及编排
 * 元数据解析（[LocalMetadataParser]）与字幕关联（[LocalSubtitleMatcher]）。
 */
object LocalMediaLoader {

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

    /**
     * 目录树拍平：返回所有文件（深度优先），与具体来源解耦可在 common 中实现，
     * 此处仍基于 Local 的 MediaInfo 类型。
     */
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

    /**
     * 递归加载整棵目录树（广度优先）。
     */
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

    /**
     * 加载单层目录：读取 ContentProvider 子项、解析媒体/字幕、触发元数据缓存命中，并关联字幕到视频。
     */
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
                        val file = File(info.name)
                        videoMap[file.nameWithoutExtension] = info
                    }
                } else {
                    val subtitleInfo = cursorLine.toSubtitleInfo()
                    if (subtitleInfo != null) {
                        subtitleList.add(subtitleInfo)
                    }
                }
            }
            // 目录扫描阶段只命中本地缓存（cacheOnly），避免全量 IPC + 远程解析拖慢加载。
            // 未命中缓存的 metadata 延迟到真正需要时由 MetadataLoader 按需解析。
            val expandList = expandFolderSync(result)
            for (file in expandList) {
                LocalMetadataParser.loadMediaMetadataSync(context, file, cacheOnly = true)
            }
            // 字幕关联
            LocalSubtitleMatcher.match(subtitleList, videoMap)
        } catch (e: Throwable) {
            log.e("loadDirectorySync", e)
        }
        log.d("loadDirectorySync path: $path result: ${result.size}")
        return result
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

    /**
     * 加载单个媒体文件（用于快速播放等场景）。
     */
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
                var mediaType = findMediaType(cursorLine.mimeType)
                if (mediaType == null) {
                    log.e("loadMediaFileSync mediaType is null， src = ${cursorLine.mimeType}, try getMimeTypeFromExtension")
                    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                        MimeTypeMap.getFileExtensionFromUrl(uri.toString()).lowercase()
                    )
                    if (mimeType != null) {
                        mediaType = findMediaType(mimeType)
                    }
                    if (mediaType == null) {
                        log.e("loadMediaFileSync mediaType is null， extension mimeType = $mimeType")
                        return@withContext null
                    }
                }
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

    /**
     * 底层 ContentProvider 子项游标枚举。
     */
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
                parentDocumentId = parentDocId
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
