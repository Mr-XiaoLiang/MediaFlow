package com.lollipop.mediaflow.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.mediaflow.tools.doAsync
import com.lollipop.mediaflow.tools.onUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

object ArchiveManager {

    private val log by lazy {
        registerLog()
    }

    private var initState = InitState.Pending

    const val FILE_NO_MEDIA = ".nomedia"
    const val FILE_CONFIG = ".config_archive"
    const val FILE_CONFIG_TEMP = ".config_archive.temp"

    fun init(context: Context) {
        val archiveDirPath = Preferences.archiveDirUri.get()
        if (archiveDirPath.isEmpty()) {
            initState = InitState.NoneDir
            return
        }
        val archiveDirUri = archiveDirPath.toUri()
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
                            TODO("读取媒体文件并且和Config对照")
                        }
                    }
                }
                if (!hasNoMedia) {
                    createNoMediaFile(context, treeUri)
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

}