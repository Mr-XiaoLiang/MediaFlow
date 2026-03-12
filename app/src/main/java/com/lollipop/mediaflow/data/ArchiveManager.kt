package com.lollipop.mediaflow.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.mediaflow.tools.doAsync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileOutputStream
import kotlin.coroutines.cancellation.CancellationException

object ArchiveManager {

    private val log by lazy {
        registerLog()
    }

    private var initState = InitState.Pending

    const val FILE_NO_MEDIA = ".nomedia"
    const val FILE_CONFIG = ".config_archive"
    const val FILE_CONFIG_TEMP = ".config_archive.temp"

    const val CONFIG_ITEMS = "items"
    const val CONFIG_ITEM_ORIGINAL_NAME = "original_name"
    const val CONFIG_ITEM_ORIGINAL_PARENT_URI = "original_parent_uri"
    const val CONFIG_ITEM_TARGET_NAME = "target_name"
    const val CONFIG_ITEM_DELETE_TIME = "delete_time"

    private var archiveUri: Uri? = null
    private var archiveName: String = ""

    var archiveConfigDelegate: ArchiveConfig = ArchiveConfigEmpty
        private set

    fun init(context: Context) {
        val archiveDirPath = Preferences.archiveDirUri.get()
        if (archiveDirPath.isEmpty()) {
            initState = InitState.NoneDir
            return
        }
        val archiveDirUri = archiveDirPath.toUri()
        archiveUri = archiveDirUri
        archiveName = Preferences.archiveDirName.get()
        initState = InitState.Running
        initManager(context, archiveDirUri)
    }

    private fun initManager(context: Context, dirUri: Uri) {
        doAsync {
            loadFileList(context, dirUri)
            // TODO
        }
    }

    private suspend fun loadFileList(context: Context, treeUri: Uri) {
        initState = withContext(Dispatchers.IO) {
            runCatching {
                var hasNoMedia = false
                val resolver = context.contentResolver
                var configContent = ""
                val fileList = mutableListOf<MediaInfo.File>()
                MediaLoader.loadDirectorySync(context, treeUri) { cursorLine ->
                    val displayName = cursorLine.displayName
                    when (displayName) {
                        FILE_NO_MEDIA -> {
                            hasNoMedia = true
                        }

                        FILE_CONFIG -> {
                            configContent = readConfig(resolver, cursorLine.fileUri)
                        }

                        else -> {
                            MediaLoader.parseToMediaInfo(
                                cursorLine = cursorLine,
                                path = archiveName
                            )?.also {
                                if (it is MediaInfo.File) {
                                    fileList.add(it)
                                }
                            }
                        }
                    }
                }
                if (!hasNoMedia) {
                    createNoMediaFile(context, treeUri)
                }
                archiveConfigDelegate = if (configContent.isEmpty()) {
                    ArchiveConfigEmpty
                } else {
                    parseConfig(configContent)
                }

                InitState.Successful
            }.fallback("loadFileList") { InitState.Error }
        }
    }

    private suspend fun readConfig(resolver: ContentResolver, fileUri: Uri): String {
        return withContext(Dispatchers.IO) {
            runCatching {
                // 直接打开输入流
                resolver.openInputStream(fileUri)?.use { inputStream ->
                    // 使用 bufferedReader 高效读取文本
                    inputStream.bufferedReader().use { reader ->
                        reader.readText()
                    }
                } ?: ""
            }.fallback("readConfig") { "" }
        }
    }

    private suspend fun readConfigByName(resolver: ContentResolver, treeUri: Uri): String {
        return queryFile(
            resolver = resolver,
            parentDocumentUri = treeUri,
            fileName = FILE_CONFIG
        )?.let {
            readConfig(resolver, it)
        } ?: ""
    }

    private suspend fun writeConfig(
        context: Context,
        treeUri: Uri,
        content: String
    ) {
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val tempUri = createFile(
                    resolver = resolver, parentDocumentUri = treeUri,
                    fileName = FILE_CONFIG_TEMP,
                    mode = FileCreateMode.DeleteOld
                ) ?: return@withContext
                // "wt" 模式：写入并截断（如果文件已存在，清空原内容再写）
                resolver.openFileDescriptor(tempUri, "wt")?.use { pfd ->
                    // 通过 FileDescriptor 构造 FileOutputStream
                    FileOutputStream(pfd.fileDescriptor).use {
                        it.write(content.toByteArray(Charsets.UTF_8))
                        it.flush()
                        try {
                            // 同步到文件，方便改名
                            pfd.fileDescriptor.sync()
                        } catch (e: Throwable) {
                            log.e("writeConfig", e)
                        }
                    }
                }
                val configUri = queryFile(resolver, treeUri, FILE_CONFIG)
                if (configUri != null) {
                    try {
                        // 只有旧文件存在时，才需要调用 deleteDocument
                        DocumentsContract.deleteDocument(resolver, configUri)
                    } catch (e: Throwable) {
                        log.e("writeConfig", e)
                    }
                }
                DocumentsContract.renameDocument(resolver, tempUri, FILE_CONFIG)
            }.logError("writeConfig")
        }
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
                TODO("没有实现")
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

    private fun parseConfig(content: String): ArchiveConfig {
        return ArchiveConfigDelegate().apply {
            runCatching {
                val jsonConfig = JSONObject(content)
                val items = jsonConfig.optJSONArray(CONFIG_ITEMS)
                val length = items?.length() ?: 0
                for (i in 0 until length) {
                    val item = items?.optJSONObject(i)
                    if (item != null) {
                        val configItem = ArchiveConfigItem(
                            originalName = item.optString(CONFIG_ITEM_ORIGINAL_NAME) ?: "",
                            originalParentUri = tryUri(
                                item.optString(
                                    CONFIG_ITEM_ORIGINAL_PARENT_URI
                                ) ?: ""
                            ),
                            targetName = item.optString(CONFIG_ITEM_TARGET_NAME) ?: "",
                            deleteTime = item.optLong(CONFIG_ITEM_DELETE_TIME)
                        )
                        itemMap[configItem.targetName] = configItem
                    }
                }
            }
        }
    }

    private fun tryUri(uri: String): Uri {
        return runCatching {
            uri.toUri()
        }.fallback("ArchiveConfigItem.tryUri") {
            Uri.EMPTY
        }
    }

    private fun buildConfig(): String {
        return runCatching {
            val items = JSONArray()
            archiveConfigDelegate.itemMap.values.forEach {
                items.put(it.toJson())
            }
            JSONObject().apply { put(CONFIG_ITEMS, items) }.toString()
        }.fallback("buildConfig") { "" }

    }

    private fun createNewFileName(): String {
        return System.currentTimeMillis().toString(16).uppercase()
    }

    private suspend fun moveDocumentFile(
        resolver: ContentResolver,
        sourceFileUri: Uri,
        originalName: String,
        sourceParentUri: Uri,
        archiveDirectoryUri: Uri
    ): ArchiveConfigItem? {
        return withContext(Dispatchers.IO) {
            runCatching {
                // 假设你已经有了源文件的 URI、源父目录 URI 和回收站目录 URI
                val movedUri = DocumentsContract.moveDocument(
                    resolver,
                    sourceFileUri,
                    sourceParentUri,
                    archiveDirectoryUri
                )

                if (movedUri == null) {
                    return@runCatching null
                }

                val newName = createNewFileName()

                val targetUri = DocumentsContract.renameDocument(
                    resolver,
                    movedUri,
                    newName
                )

                if (targetUri == null) {
                    return@runCatching null
                }

                val finalName = findFileName(resolver, targetUri)

                ArchiveConfigItem(
                    originalName = originalName,
                    originalParentUri = sourceParentUri,
                    targetName = finalName,
                    deleteTime = System.currentTimeMillis()
                )
            }.fallback("moveDocumentFile") { null }
        }
    }

    private suspend fun moveStreamFile(
        resolver: ContentResolver,
        sourceUri: Uri,
        originalName: String,
        sourceParentUri: Uri,
        archiveDirectoryUri: Uri,
        onProgress: (Float) -> Unit
    ): ArchiveConfigItem? {
        return withContext(Dispatchers.IO) { // 切换到 IO 线程执行
            runCatching {
                val newName = createNewFileName()

                val totalSize = getFileSize(resolver, sourceUri)
                if (totalSize < 1) {
                    log.e("moveStreamFile, totalSize < 1, sourceUri = $sourceUri")
                    return@runCatching null
                }

                // 1. 获取源文件的 MIME 类型
                val mimeType = resolver.getType(sourceUri) ?: "application/octet-stream"

                // 2. 在目标位置创建新文件
                val newFileUri = DocumentsContract.createDocument(
                    resolver,
                    archiveDirectoryUri,
                    mimeType,
                    newName
                )

                // 如果创建失败，就放弃了
                if (newFileUri == null) {
                    log.e("moveStreamFile, newFileUri == null, archiveDirectoryUri = $archiveDirectoryUri, mimeType = $mimeType, newName = $newName")
                    return@runCatching null
                }

                val finalName = findFileName(resolver, newFileUri)

                if (finalName.isEmpty()) {
                    log.e("moveStreamFile, finalName == null, newFileUri = $newFileUri")
                    DocumentsContract.deleteDocument(resolver, newFileUri)
                    return@runCatching null
                }

                // 3. 使用 Kotlin 的 .use 扩展函数处理流，会自动 close
                val sourceStream = resolver.openInputStream(sourceUri)
                if (sourceStream == null) {
                    // 如果找不到源文件，那么就删除创建的文件
                    DocumentsContract.deleteDocument(resolver, newFileUri)
                    log.e("moveStreamFile, sourceStream == null, sourceUri = $sourceUri")
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

                                val newProgress = (bytesCopied.toFloat() / totalSize).coerceIn(0f, 1f)
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
                DocumentsContract.deleteDocument(resolver, sourceUri)

                // 返回回收结果
                ArchiveConfigItem(
                    originalName = originalName,
                    originalParentUri = sourceParentUri,
                    targetName = finalName,
                    deleteTime = System.currentTimeMillis()
                )
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
        Running,
        Successful,
        Error

    }

    interface ArchiveConfig {
        val itemMap: Map<String, ArchiveConfigItem>
    }

    private class ArchiveConfigDelegate : ArchiveConfig {

        override val itemMap = mutableMapOf<String, ArchiveConfigItem>()

    }

    private object ArchiveConfigEmpty : ArchiveConfig {
        override val itemMap: Map<String, ArchiveConfigItem> by lazy {
            emptyMap()
        }
    }

    class ArchiveConfigItem(
        /**
         * 原始名称
         */
        val originalName: String,

        /**
         * 原始文件夹
         */
        val originalParentUri: Uri,

        /**
         * 当前的文件名
         */
        val targetName: String,

        /**
         * 删除时间
         */
        val deleteTime: Long,
    ) {

        fun toJson(): JSONObject {
            return JSONObject().apply {
                put(CONFIG_ITEM_ORIGINAL_NAME, originalName)
                put(CONFIG_ITEM_ORIGINAL_PARENT_URI, originalParentUri.toString())
                put(CONFIG_ITEM_TARGET_NAME, targetName)
                put(CONFIG_ITEM_DELETE_TIME, deleteTime)
            }
        }
    }

}