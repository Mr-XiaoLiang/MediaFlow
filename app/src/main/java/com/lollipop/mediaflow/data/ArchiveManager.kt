package com.lollipop.mediaflow.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.mediaflow.tools.doAsync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.cancellation.CancellationException

object ArchiveManager {

    private val log by lazy {
        registerLog()
    }

    private var initState = InitState.Pending

    const val FILE_NO_MEDIA = ".nomedia"

    const val PROGRESS_INFINITE = -1F
    const val PROGRESS_COMPLETE = 1F

    private var archiveUri: Uri? = null
    private var archiveName: String = ""

    val archiveTaskList = mutableStateListOf<ArchiveTask>()
    val historyTaskList = mutableStateListOf<ArchiveTask>()
    private var contextRef: WeakReference<Context>? = null

    private val timeFormatter by lazy {
        DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
    }

    val isQuickEnable: Boolean
        get() {
            return initState == InitState.Successful && Preferences.isQuickArchiveEnable.get()
        }

    fun init(context: Context) {
        val app = context.applicationContext
        contextRef = WeakReference(app)
        if (initState == InitState.Successful) {
            return
        }
        val archiveDirPath = Preferences.archiveDirUri.get()
        if (archiveDirPath.isEmpty()) {
            initState = InitState.NoneDir
            return
        }
        val archiveDocUri = runCatching {
            val archiveDirUri = archiveDirPath.toUri()
            // 2. 提取出它的 DocumentId (即 primary:回收站 这部分)
            val docId = DocumentsContract.getTreeDocumentId(archiveDirUri)
            // 3. 构建合法的 Document URI
            DocumentsContract.buildDocumentUriUsingTree(archiveDirUri, docId)
        }.getOrNull() ?: Uri.EMPTY
        if (archiveDocUri == Uri.EMPTY) {
            initState = InitState.NoneDir
            return
        }
        archiveUri = archiveDocUri
        archiveName = Preferences.archiveDirName.get()
        initState = InitState.Successful
        initManager(app, archiveDocUri)
    }

    private fun initManager(context: Context, dirUri: Uri) {
        doAsync {
            loadFileList(context, dirUri)
        }
    }

    private suspend fun loadFileList(context: Context, treeUri: Uri) {
        withContext(Dispatchers.IO) {
            runCatching {
                val noMedia = readConfigByName(context.contentResolver, treeUri, FILE_NO_MEDIA)
                if (noMedia == null) {
                    createNoMediaFile(context, treeUri)
                }
            }.fallback("loadFileList") { InitState.Error }
        }
    }

    fun moveToArchive(context: Context, mediaInfo: MediaInfo.File): ArchiveTask? {
        contextRef = WeakReference(context)
        val archiveDirectoryUri = archiveUri ?: return null
        val sourceUri = mediaInfo.uri
        try {
            archiveTaskList.find { it.uri == sourceUri }?.let {
                return it
            }
        } catch (e: Throwable) {
            log.e("moveToArchive", e)
            return null
        }
        val sourceName = mediaInfo.name

        val taskInfo = ArchiveTask(
            uri = sourceUri,
            fileName = sourceName,
        )
        doAsync {
            val sourceParentUri = DocumentsContract.buildDocumentUriUsingTree(
                mediaInfo.rootUri,
                mediaInfo.parentDocId
            )
            val resolver = context.contentResolver


            archiveTaskList.add(taskInfo)
            taskInfo.progressState = PROGRESS_INFINITE
            val targetName = createNewFileName(sourceName)
            val moveDocumentResult = moveDocumentFile(
                resolver = resolver,
                sourceFileUri = sourceUri,
                sourceParentUri = sourceParentUri,
                targetName = targetName,
                targetDirectoryUri = archiveDirectoryUri
            )
            if (moveDocumentResult == null) {
                moveStreamFile(
                    resolver = resolver,
                    sourceFileUri = sourceUri,
                    targetName = targetName,
                    targetDirectoryUri = archiveDirectoryUri,
                    onProgress = {
                        taskInfo.progressState = it
                    },
                )
            }
            taskInfo.progressState = PROGRESS_COMPLETE
            archiveTaskList.remove(taskInfo)
            historyTaskList.add(taskInfo)
        }
        return taskInfo
    }

    private suspend fun readConfigByName(
        resolver: ContentResolver,
        treeUri: Uri,
        fileName: String
    ): Uri? {
        return queryFile(
            resolver = resolver,
            parentDocumentUri = treeUri,
            fileName = fileName
        )
    }

    private suspend fun createNoMediaFile(context: Context, parentDocumentUri: Uri) {
        createFile(
            resolver = context.contentResolver,
            parentDocumentUri = parentDocumentUri,
            fileName = FILE_NO_MEDIA,
            mode = FileCreateMode.KeepOld
        )
    }

    private suspend fun findFileName(
        resolver: ContentResolver,
        fileUri: Uri
    ): String {
        return withContext(Dispatchers.IO) {
            runCatching {
                val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                resolver.query(fileUri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                    } else {
                        null
                    }
                } ?: ""
            }.fallback("findFileName") { "" }
        }
    }

    private suspend fun queryFile(
        resolver: ContentResolver,
        parentDocumentUri: Uri,
        fileName: String
    ): Uri? {
        return withContext(Dispatchers.IO) {
            runCatching {
                // 1. 定向查询文件名，获取它的唯一 ID
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    parentDocumentUri,
                    DocumentsContract.getTreeDocumentId(parentDocumentUri)
                )

                val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                // 这里的 selection 就是 SQL 过滤条件
                val selection = "${DocumentsContract.Document.COLUMN_DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(fileName)

                val docId = resolver.query(
                    childrenUri,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(0)
                    } else {
                        null
                    }
                }
                docId?.let {
                    DocumentsContract.buildDocumentUriUsingTree(
                        parentDocumentUri,
                        docId
                    )
                }
            }.fallback("queryFile") { null }
        }
    }

    private suspend fun createFile(
        resolver: ContentResolver,
        parentDocumentUri: Uri,
        fileName: String,
        mode: FileCreateMode = FileCreateMode.DeleteOld
    ): Uri? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val oldUri = queryFile(resolver, parentDocumentUri, fileName)
                if (oldUri != null) {
                    when (mode) {
                        FileCreateMode.DeleteOld -> {
                            // 2. 拿到精准 Uri 后，调用专门的删除函数
                            DocumentsContract.deleteDocument(resolver, oldUri)
                        }

                        FileCreateMode.KeepOld -> {
                            return@withContext oldUri
                        }
                    }
                }
            }.logError("createFile")
            runCatching {
                // 直接通过 DocumentsContract 创建文档
                // 参数1: ContentResolver
                // 参数2: 父目录的 DocumentUri
                // 参数3: MIME 类型 (对于 .nomedia，通常用 application/octet-stream)
                // 参数4: 显示的文件名
                return@withContext DocumentsContract.createDocument(
                    resolver,
                    parentDocumentUri,
                    "application/octet-stream",
                    fileName
                )
            }.fallback("createFile") { null }
        }
    }

    private inline fun <reified T> Result<T>.logError(where: String) {
        exceptionOrNull()?.let {
            log.e(where, it)
        }
    }

    private inline fun <reified T> Result<T>.fallback(where: String, defBlock: () -> T): T {
        return this.getOrElse { throwable ->
            log.e(where, throwable)
            defBlock()
        }
    }

    private fun createNewFileName(sourceName: String): String {
        return "MF${LocalDateTime.now().format(timeFormatter)}-${sourceName}"
    }

    private suspend fun moveDocumentFile(
        resolver: ContentResolver,
        sourceFileUri: Uri,
        sourceParentUri: Uri,
        targetName: String,
        targetDirectoryUri: Uri
    ): Uri? {
        return withContext(Dispatchers.IO) {
            runCatching {
                // 假设你已经有了源文件的 URI、源父目录 URI 和回收站目录 URI
                val movedUri = DocumentsContract.moveDocument(
                    resolver,
                    sourceFileUri,
                    sourceParentUri,
                    targetDirectoryUri
                )

                if (movedUri == null) {
                    return@runCatching null
                }

                // 从返回的 movedUri 中提取出它在 Provider 中的真实 DocumentId
                val movedDocId = DocumentsContract.getDocumentId(movedUri)

                // 这里的 rootTreeUriOfTarget 是你最初通过 ACTION_OPEN_DOCUMENT_TREE 获得的回收站根 URI
                val reAnchoredUri = DocumentsContract.buildDocumentUriUsingTree(
                    targetDirectoryUri,
                    movedDocId
                )

                DocumentsContract.renameDocument(
                    resolver,
                    reAnchoredUri,
                    targetName
                )
            }.fallback("moveDocumentFile") { null }
        }
    }

    private suspend fun moveStreamFile(
        resolver: ContentResolver,
        sourceFileUri: Uri,
        targetName: String,
        targetDirectoryUri: Uri,
        onProgress: (Float) -> Unit
    ): Uri? {
        return withContext(Dispatchers.IO) { // 切换到 IO 线程执行
            runCatching {

                val totalSize = getFileSize(resolver, sourceFileUri)
                if (totalSize < 1) {
                    log.e("moveStreamFile, totalSize < 1, sourceUri = $sourceFileUri")
                    return@runCatching null
                }

                // 1. 获取源文件的 MIME 类型
                val mimeType = resolver.getType(sourceFileUri) ?: "application/octet-stream"

                // 2. 在目标位置创建新文件
                val newFileUri = DocumentsContract.createDocument(
                    resolver,
                    targetDirectoryUri,
                    mimeType,
                    targetName
                )

                // 如果创建失败，就放弃了
                if (newFileUri == null) {
                    log.e("moveStreamFile, newFileUri == null, targetDirectoryUri = $targetDirectoryUri, mimeType = $mimeType, newName = $targetName")
                    return@runCatching null
                }

                val finalName = findFileName(resolver, newFileUri)

                if (finalName.isEmpty()) {
                    log.e("moveStreamFile, finalName == null, newFileUri = $newFileUri")
                    DocumentsContract.deleteDocument(resolver, newFileUri)
                    return@runCatching null
                }

                // 3. 使用 Kotlin 的 .use 扩展函数处理流，会自动 close
                val sourceStream = resolver.openInputStream(sourceFileUri)
                if (sourceStream == null) {
                    // 如果找不到源文件，那么就删除创建的文件
                    DocumentsContract.deleteDocument(resolver, newFileUri)
                    log.e("moveStreamFile, sourceStream == null, sourceUri = $sourceFileUri")
                    return@runCatching null
                }

                try {
                    sourceStream.use { inputStream ->

                        val targetStream = resolver.openOutputStream(newFileUri)
                        if (targetStream == null) {
                            // 如果打不开源文件，就删除新文件，放弃吧
                            DocumentsContract.deleteDocument(resolver, newFileUri)
                            log.e("moveStreamFile, targetStream == null, newFileUri = $newFileUri")
                            return@runCatching null
                        }

//                        targetStream.use { outputStream ->
//                            // 高效拷贝：每次读取 8KB
//                            inputStream.copyTo(outputStream)
//                            // 推出
//                            outputStream.flush()
//                        }

                        targetStream.use { output ->
                            // --- 手动拷贝逻辑开始 ---
                            val buffer = ByteArray(8 * 1024) // 8KB 缓冲区
                            var bytesCopied = 0L
                            // 在循环外定义
                            var lastProgress = 0f

                            do {
                                // 检查协程是否已被取消（用户点击取消按钮）
                                if (!isActive) {
                                    throw CancellationException("User cancelled the move")
                                }
                                val read = inputStream.read(buffer)
                                if (read < 0) {
                                    break
                                }
                                output.write(buffer, 0, read)
                                bytesCopied += read

                                val newProgress =
                                    (bytesCopied.toFloat() / totalSize).coerceIn(0f, 1f)
                                if (newProgress - lastProgress >= 0.01F || bytesCopied >= totalSize) {
                                    lastProgress = newProgress
                                    // 回调进度
                                    onProgress(newProgress)
                                }
                            } while (true)
                            output.flush()
                            // --- 手动拷贝逻辑结束 ---
                        }
                    }
                } catch (e: Throwable) {
                    // 拷贝过程中断、源文件被删、磁盘满等任何情况，都要清理回收站里的“残骸”
                    DocumentsContract.deleteDocument(resolver, newFileUri)
                    log.e("moveStreamFile", e)
                    return@runCatching null
                }

                // 4. 拷贝成功后，删除原文件
                DocumentsContract.deleteDocument(resolver, sourceFileUri)

                newFileUri
            }.fallback("moveStreamFile") {
                null
            }
        }
    }

    /** 辅助函数：获取 SAF 文件大小 */
    private fun getFileSize(resolver: ContentResolver, uri: Uri): Long {
        return runCatching {
            resolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_SIZE),
                null,
                null,
                null
            )?.use {
                if (it.moveToFirst()) {
                    it.getLong(0)
                } else {
                    0L
                }
            } ?: 0L
        }.fallback("getFileSize") { 0L }
    }

    private enum class FileCreateMode {
        DeleteOld,
        KeepOld
    }

    enum class InitState {

        Pending,
        NoneDir,
        Successful,
        Error

    }

    class ArchiveTask(
        val uri: Uri,
        val fileName: String,
    ) {

        var progressState by mutableFloatStateOf(PROGRESS_INFINITE)

    }

}