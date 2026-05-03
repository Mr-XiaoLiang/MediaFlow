package com.lollipop.mediaflow.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import com.lollipop.mediaflow.R
import com.lollipop.common.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.common.tools.doAsync
import com.lollipop.common.tools.onUI
import com.lollipop.common.tools.task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.LinkedList
import kotlin.coroutines.cancellation.CancellationException

object ArchiveManager {

    private const val CONFIG_LIST = "config_list"
    private const val CONFIG_NAME = "config_name"
    private const val CONFIG_URI = "config_uri"

    private const val CONFIG_FAVORITE = "favorite"
    private const val CONFIG_SPECIAL = "special"
    private const val CONFIG_THUMP_UP = "thump_up"

    private val log by lazy {
        registerLog()
    }

    private val initStateImpl = mutableStateOf<InitState>(InitState.Pending)

    private val favoriteBasket = mutableStateOf<ArchiveBasket?>(null)
    private val specialBasket = mutableStateOf<ArchiveBasket?>(null)
    private val thumpUpBasket = mutableStateOf<ArchiveBasket?>(null)

    const val FILE_NO_MEDIA = ".nomedia"

    const val PROGRESS_INFINITE = -1F
    const val PROGRESS_COMPLETE = 1F

    val initState: State<InitState>
        get() {
            return initStateImpl
        }

    val favorite: State<ArchiveBasket?>
        get() {
            return favoriteBasket
        }

    val special: State<ArchiveBasket?>
        get() {
            return specialBasket
        }

    val thumpUp: State<ArchiveBasket?>
        get() {
            return thumpUpBasket
        }

    /**
     * 回收任务的列表
     */
    val archiveTaskList = mutableStateListOf<ArchiveTask>()

    /**
     * 历史任务的列表
     */
    val historyTaskList = mutableStateListOf<ArchiveTask>()

    /**
     * 回收站篮子的集合
     */
    val archiveBasketList = mutableStateListOf<ArchiveBasket>()

    /**
     * 待执行的回收任务
     */
    private val pendingList = LinkedList<ArchiveTask>()
    private var contextRef: WeakReference<Context>? = null

    private val timeFormatter by lazy {
        DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
    }

    private val config by lazy {
        ConfigHelper("archive_manager")
    }

    private val pendingSaveTask = task {
        saveConfig()
    }

    fun init(context: Context) {
        val app = context.applicationContext
        contextRef = WeakReference(app)
        if (initState.value == InitState.Successful) {
            return
        }
        initStateImpl.value = InitState.Loading
        config.load(context) {
            onConfigLoaded(app)
        }
    }

    /**
     * 获取篮子类型
     */
    fun getBasketType(basket: ArchiveBasket): ArchiveQuick {
        if (favoriteBasket.value?.uriString == basket.uriString) {
            return ArchiveQuick.Favorite
        }
        if (specialBasket.value?.uriString == basket.uriString) {
            return ArchiveQuick.Special
        }
        if (thumpUpBasket.value?.uriString == basket.uriString) {
            return ArchiveQuick.ThumpUp
        }
        return ArchiveQuick.Other
    }

    fun isQuickEnable(type: ArchiveQuick): Boolean {
        if (initState.value != InitState.Successful) {
            return false
        }
        if (!Preferences.isQuickArchiveEnable.get()) {
            return false
        }
        when (type) {
            ArchiveQuick.Favorite -> {
                return favoriteBasket.value != null
            }

            ArchiveQuick.Special -> {
                return specialBasket.value != null
            }

            ArchiveQuick.ThumpUp -> {
                return thumpUpBasket.value != null
            }

            ArchiveQuick.Other -> {
                if (!Preferences.isShowOtherQuickArchiveButton.get()) {
                    return false
                }
                return archiveBasketList.isNotEmpty()
            }
        }
    }

    fun addBasket(context: Context, name: String, uri: Uri) {
        updateContext(context)
        archiveBasketList.add(ArchiveBasket(name = name, uri = uri))
        pendingSaveTask.delayOnIO(500)
    }

    fun removeBasket(context: Context, basket: ArchiveBasket) {
        updateContext(context)
        val uriString = basket.uriString
        archiveBasketList.removeIf { it.uriString == uriString }
        if (favoriteBasket.value?.uriString == uriString) {
            favoriteBasket.value = null
        }
        if (specialBasket.value?.uriString == uriString) {
            specialBasket.value = null
        }
        if (thumpUpBasket.value?.uriString == uriString) {
            thumpUpBasket.value = null
        }
        pendingSaveTask.delayOnIO(500)
    }

    fun setQuick(context: Context, type: ArchiveQuick, basket: ArchiveBasket) {
        updateContext(context)
        // 需要先移除原来的
        favoriteBasket.value?.let {
            if (it.uriString == basket.uriString) {
                favoriteBasket.value = null
            }
        }
        specialBasket.value?.let {
            if (it.uriString == basket.uriString) {
                specialBasket.value = null
            }
        }
        thumpUpBasket.value?.let {
            if (it.uriString == basket.uriString) {
                thumpUpBasket.value = null
            }
        }

        when (type) {
            ArchiveQuick.Favorite -> {
                favoriteBasket.value = basket
            }

            ArchiveQuick.Special -> {
                specialBasket.value = basket
            }

            ArchiveQuick.ThumpUp -> {
                thumpUpBasket.value = basket
            }

            ArchiveQuick.Other -> {
                // 这里就是好不设置的意思
            }
        }
        pendingSaveTask.delayOnIO(500)
    }

    private fun updateContext(context: Context) {
        contextRef = WeakReference(context.applicationContext)
    }

    private fun saveConfig() {

        val uriMap = HashMap<String, ArchiveBasket>()

        archiveBasketList.forEach {
            uriMap[it.uriString] = it
        }

        val newArray = JSONArray()

        uriMap.values.forEach { info ->
            newArray.put(
                JSONObject().also {
                    it.put(CONFIG_NAME, info.name)
                    it.put(CONFIG_URI, info.uri.toString())
                }
            )
        }

        val jsonConfig = config.jsonConfig
        jsonConfig.remove(CONFIG_LIST)
        jsonConfig.put(CONFIG_LIST, newArray)
        jsonConfig.put(CONFIG_FAVORITE, favoriteBasket.value?.uriString ?: "")
        jsonConfig.put(CONFIG_SPECIAL, specialBasket.value?.uriString ?: "")
        jsonConfig.put(CONFIG_THUMP_UP, thumpUpBasket.value?.uriString ?: "")

        config.save(contextRef?.get())
    }

    private fun onConfigLoaded(app: Context) {
        doAsync {
            val tempList = ArrayList<ArchiveBasket>()
            val jsonConfig = config.jsonConfig
            val array = jsonConfig.optJSONArray(CONFIG_LIST)
            if (array != null) {
                val length = array.length()
                for (i in 0 until length) {
                    try {
                        val obj = array.optJSONObject(i)
                        val name = obj.optString(CONFIG_NAME)
                        val uriValue = obj.optString(CONFIG_URI)
                        if (uriValue.isNotEmpty()) {
                            val uri = uriValue.toUri()
                            if (uri != Uri.EMPTY) {
                                if (MediaChooser.hasWritePermission(app, uriValue)) {
                                    tempList.add(ArchiveBasket(name = name, uri = uri))
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        log.e("onConfigLoaded", e)
                    }
                }
            }
            val favoriteUriString = jsonConfig.optString(CONFIG_FAVORITE)
            val specialUriString = jsonConfig.optString(CONFIG_SPECIAL)
            val thumpUpUriString = jsonConfig.optString(CONFIG_THUMP_UP)
            var favorite: ArchiveBasket? = null
            var special: ArchiveBasket? = null
            var thumpUp: ArchiveBasket? = null
            tempList.forEach {
                if (it.uriString == favoriteUriString) {
                    favorite = it
                }
                if (it.uriString == specialUriString) {
                    special = it
                }
                if (it.uriString == thumpUpUriString) {
                    thumpUp = it
                }
                checkNoMediaFile(app, it.docUri)
            }
            onUI {
                archiveBasketList.clear()
                archiveBasketList.addAll(tempList)
                favoriteBasket.value = favorite
                specialBasket.value = special
                thumpUpBasket.value = thumpUp
                initStateImpl.value = if (archiveBasketList.isEmpty()) {
                    InitState.NoneDir
                } else {
                    InitState.Successful
                }
            }
            flushPending(app)
        }
    }

    private suspend fun checkNoMediaFile(context: Context, treeUri: Uri) {
        withContext(Dispatchers.IO) {
            runCatching {
                val noMedia = readConfigByName(context.contentResolver, treeUri, FILE_NO_MEDIA)
                if (noMedia == null) {
                    createNoMediaFile(context, treeUri)
                }
            }.fallback("loadFileList") { InitState.Error }
        }
    }

    fun moveToArchive(
        context: Context,
        basket: ArchiveBasket,
        mediaInfo: MediaInfo.File
    ): ArchiveTask? {
        contextRef = WeakReference(context)
        val archiveDirectoryUri = basket.docUri
        val sourceUri = mediaInfo.uri
        try {
            archiveTaskList.find { it.sourceUri == sourceUri }?.let {
                return it
            }
            pendingList.find { it.sourceUri == sourceUri }?.let {
                return it
            }
        } catch (e: Throwable) {
            log.e("moveToArchive", e)
            return null
        }
        val sourceName = mediaInfo.name
        val sourceParentUri = DocumentsContract.buildDocumentUriUsingTree(
            mediaInfo.rootUri,
            mediaInfo.parentDocId
        )

        val taskInfo = ArchiveTask(
            sourceUri = sourceUri,
            sourceName = sourceName,
            archiveDirectoryUri = archiveDirectoryUri,
            sourceParentUri = sourceParentUri,
        )
        if (initState.value == InitState.Loading) {
            pendingList.add(taskInfo)
            return taskInfo
        }
        if (initState.value == InitState.Pending) {
            pendingList.add(taskInfo)
            init(context)
            return taskInfo
        }

        // 检查一下历史信息
        flushPending(context)
        // 移动当前的
        doMove(context, taskInfo)
        return taskInfo
    }

    private fun flushPending(context: Context) {
        while (pendingList.isNotEmpty()) {
            val archiveTask = pendingList.removeFirst()
            doMove(context, archiveTask)
        }
    }

    private fun doMove(context: Context, taskInfo: ArchiveTask) {
        doAsync {
            val archiveDirectoryUri = taskInfo.archiveDirectoryUri
            val sourceParentUri = taskInfo.sourceParentUri
            val resolver = context.contentResolver
            val sourceUri = taskInfo.sourceUri
            val sourceName = taskInfo.sourceName

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
        Loading,
        NoneDir,
        Successful,
        Error

    }

    class ArchiveTask(
        val sourceUri: Uri,
        val sourceName: String,
        val archiveDirectoryUri: Uri,
        val sourceParentUri: Uri,
    ) {

        var progressState by mutableFloatStateOf(PROGRESS_INFINITE)

    }

}

enum class ArchiveQuick(
    val iconRes: Int,
    val labelRes: Int
) {
    Favorite(iconRes = R.drawable.favorite_24, labelRes = R.string.label_quick_archive_favorite),
    Special(iconRes = R.drawable.star_24, labelRes = R.string.label_quick_archive_special),
    ThumpUp(iconRes = R.drawable.thumb_up_24, labelRes = R.string.label_quick_archive_thump_up),
    Other(iconRes = R.drawable.archive_24, labelRes = R.string.label_quick_archive_other)
}

class ArchiveBasket(
    val name: String,
    val uri: Uri
) {

    val uriString: String by lazy {
        uri.toString()
    }

    val uriPath: String by lazy {
        uri.path ?: ""
    }

    val docId: String by lazy {
        DocumentsContract.getTreeDocumentId(uri)
    }

    val docUri: Uri by lazy {
        DocumentsContract.buildDocumentUriUsingTree(uri, docId)
    }

}