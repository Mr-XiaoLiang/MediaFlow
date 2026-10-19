package com.lollipop.mediaflow.data.local

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.lollipop.mediaflow.data.LMedia
import com.lollipop.mediaflow.data.source.ArchiveBackend
import com.lollipop.mediaflow.data.source.ArchiveSubject
import com.lollipop.mediaflow.data.source.SourceId
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.TaskResult
import com.lollipop.mediaflow.tools.mapTo
import com.lollipop.mediaflow.tools.mapValue
import com.lollipop.mediaflow.tools.onFailure
import com.lollipop.mediaflow.tools.safeNoNull
import com.lollipop.mediaflow.tools.safeRun
import com.lollipop.mediaflow.tools.useNoNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.cancellation.CancellationException

/**
 * Local 协议的归档实现（来源轴）。
 *
 * 承接原 [ArchiveManager] 的本地归档**执行原语**：
 * - 重命名（含归档重锚定）与原始名还原；
 * - 同根 `moveDocument` 搬运，跨根或失败时降级为 `moveStreamFile` 流式拷贝；
 * - 归档点目录的 `.nomedia` 保障。
 *
 * 行为与原实现**等价**，存储读写保持原格式。
 *
 * ## 边界
 * - 本类只提供执行原语与命名规则，**不持有**任务队列 / 进度 / 历史（仍由 [ArchiveManager] 编排）；
 * - 只负责"基于 SAF 的实际搬运"，不感知 UI、不做来源分派。
 *
 * ## 线程约定
 * 涉及 SAF 的 IO 一律在 [Dispatchers.IO] 执行（各方法内部 `withContext`）。
 */
object LocalArchiveBackend : ArchiveBackend {

    override val sourceId: SourceId = SourceId.Local

    /** Local 的归档句柄：持有锚定的原始项（含 `rootUri` / `parentDocId`）。 */
    private class LocalArchiveSubject(val file: MediaInfo.File) : ArchiveSubject {
        // 去重维度由 Local 决定：同一文件地址即同一归档目标
        override val tag: String get() = file.uriString
        override val description: String get() = file.name
    }

    /** 归档点目录的"忽略媒体扫描"标记文件。 */
    private const val FILE_NO_MEDIA = ".nomedia"

    /** 归档重命名前缀 / 后缀 / 时间戳格式（与既有行为一致，勿改动）。 */
    private const val RENAME_PREFIX = "MF"
    private const val RENAME_SUFFIX = "-"
    private const val RENAME_TIME_FORMAT = "yyyyMMddHHmmssSSS"

    private val log by lazy {
        registerLog()
    }

    private val timeFormatter by lazy {
        DateTimeFormatter.ofPattern(RENAME_TIME_FORMAT)
    }

    // ---------------------------------------------------------------------
    // 重命名
    // ---------------------------------------------------------------------

    /**
     * 就地重命名单个文件（归档重命名页使用）。
     *
     * @return 成功时返回重命名后的 Uri。
     */
    suspend fun rename(
        resolver: ContentResolver,
        file: MediaInfo.File,
        newName: String
    ): TaskResult<Uri> {
        return withContext(Dispatchers.IO) {
            safeRun {
                DocumentsContract.renameDocument(
                    resolver,
                    file.uri,
                    newName
                )!!
            }.onFailure {
                log.e("rename failed", it)
            }
        }
    }

    /**
     * 重命名并重锚定到原根树。
     *
     * 归档搬运前先把文件重命名为 `MF{时间戳}-原始名`，并把返回的 Uri 重新锚定到
     * 该文件所属的根树（`rootUri`），保证后续 `moveDocument` 使用同树 Uri。
     */
    private suspend fun renameAndReanchored(
        resolver: ContentResolver,
        fileUri: Uri,
        rootUri: Uri,
        newName: String
    ): TaskResult<Uri> {
        return withContext(Dispatchers.IO) {
            safeNoNull {
                DocumentsContract.renameDocument(
                    resolver,
                    fileUri,
                    newName
                )
            }.mapValue { movedUri ->
                DocumentsContract.getDocumentId(movedUri)
            }.mapValue { movedDocId ->
                DocumentsContract.buildDocumentUriUsingTree(
                    rootUri,
                    movedDocId
                )
            }.onFailure {
                log.e("renameAndReanchored failed", it)
            }
        }
    }

    /**
     * 去掉归档重命名前缀，还原原始文件名。
     *
     * 规则：允许出现**多段** `MF{17 位数字}-` 前缀（重复归档），从头部连续剥离。
     */
    fun restoreOriginalFileName(currentName: String): String {
        val len = currentName.length
        val prefixLength = RENAME_PREFIX.length
        val timestampLength = RENAME_TIME_FORMAT.length
        val suffixLength = RENAME_SUFFIX.length
        val totalPrefixLength = prefixLength + timestampLength + suffixLength
        val suffixOffset = totalPrefixLength - suffixLength
        var index = 0

        while (index + prefixLength <= len) {
            // 1. 🔥 优雅核心：直接比对指定区域是否匹配 RENAME_PREFIX 和 RE_SUFFIX
            // 参数依次为：自身起始偏移、目标字符串、目标起始偏移、比对长度
            val hasPrefix = currentName.regionMatches(index, RENAME_PREFIX, 0, prefixLength)
            val hasSuffix =
                currentName.regionMatches(index + suffixOffset, RENAME_SUFFIX, 0, suffixLength)

            if (hasPrefix && hasSuffix) {
                // 2. 检查中间 17 位是否全是数字
                var isDateTime = true
                val digitStart = index + prefixLength
                val digitEnd = digitStart + timestampLength

                for (i in digitStart until digitEnd) {
                    if (!currentName[i].isDigit()) {
                        isDateTime = false
                        break
                    }
                }

                if (isDateTime) {
                    index += totalPrefixLength // 验证通过，指针跳过这段前缀
                } else {
                    break
                }
            } else {
                break
            }
        }

        // 整个函数运行下来，只有这里创建了唯一一个新字符串
        return if (index == 0) currentName else currentName.substring(index)
    }

    // ---------------------------------------------------------------------
    // 归档点保障
    // ---------------------------------------------------------------------

    /** 确保归档点目录存在 `.nomedia`（不存在则创建，已存在则保留）。 */
    suspend fun checkNoMediaFile(context: Context, treeUri: Uri) {
        withContext(Dispatchers.IO) {
            readConfigByName(
                context.contentResolver,
                treeUri,
                FILE_NO_MEDIA
            ).onFailure {
                createNoMediaFile(context, treeUri)
            }.onFailure {
                log.e("checkNoMediaFile", it)
            }
        }
    }

    // ---------------------------------------------------------------------
    // 搬运原语
    // ---------------------------------------------------------------------

    /** 按既有规则生成归档目标文件名：`MF{yyyyMMddHHmmssSSS}-原始名`。 */
    private fun createNewFileName(sourceName: String): String {
        val builder = StringBuilder()
        builder.append(RENAME_PREFIX)
        builder.append(LocalDateTime.now().format(timeFormatter))
        builder.append(RENAME_SUFFIX)
        builder.append(sourceName)
        return builder.toString()
    }

    // ---------------------------------------------------------------------
    // ArchiveBackend 实现 / 完整搬运
    // ---------------------------------------------------------------------

    /**
     * 锁定：在集合移除**之前**，用**内存映射**取回原始项（含 `rootUri` / `parentDocId`），
     * 包成句柄返回——由任务持有，此后前台移除集合也不影响归档。
     */
    override suspend fun prepare(item: LMedia): ArchiveSubject? {
        val file = LocalMediaBackend.findLocalFile(item.mediaId)
        if (file == null) {
            log.e("prepare: local file not found, mediaId = ${item.mediaId}")
            return null
        }
        return LocalArchiveSubject(file)
    }

    /**
     * 执行：用 [prepare] 产出的句柄走 SAF 搬运（见 [move]）。
     */
    override suspend fun archive(
        context: Context,
        subject: ArchiveSubject,
        basket: ArchiveBasket,
        onProgress: (Float) -> Unit
    ): Boolean {
        // 同一来源产出 / 消费：句柄必为本实现类型。
        val local = subject as? LocalArchiveSubject ?: run {
            log.e("archive: unexpected subject = ${subject::class.simpleName}")
            return false
        }
        val file = local.file
        val sourceParentUri = DocumentsContract.buildDocumentUriUsingTree(
            file.rootUri,
            file.parentDocId
        )
        return move(
            context = context,
            sourceUri = file.uri,
            sourceName = file.name,
            sourceParentUri = sourceParentUri,
            sourceRootUri = file.rootUri,
            archiveDirectoryUri = basket.docUri,
            onProgress = onProgress
        )
    }

    /**
     * 执行一次完整搬运：重命名（重锚定到原根）→ 同根 `moveDocument`，
     * 失败则降级 [moveStreamFile] 流式拷贝。
     *
     * 与既有行为等价：降级拷贝失败会清理目标侧半成品，**源文件原地不动**。
     *
     * @return 是否成功。
     */
    private suspend fun move(
        context: Context,
        sourceUri: Uri,
        sourceName: String,
        sourceParentUri: Uri,
        sourceRootUri: Uri,
        archiveDirectoryUri: Uri,
        onProgress: (Float) -> Unit
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val targetName = createNewFileName(sourceName)

            val renamedUri = renameAndReanchored(
                resolver = resolver,
                fileUri = sourceUri,
                rootUri = sourceRootUri,
                newName = targetName
            ).getOrNull()

            val movedUri = if (renamedUri != null) {
                moveDocumentFile(
                    resolver = resolver,
                    sourceFileUri = renamedUri,
                    sourceParentUri = sourceParentUri,
                    targetName = targetName,
                    targetDirectoryUri = archiveDirectoryUri
                ).getOrNull()
            } else {
                null
            }

            if (movedUri != null) {
                true
            } else {
                moveStreamFile(
                    resolver = resolver,
                    sourceFileUri = renamedUri ?: sourceUri,
                    targetName = targetName,
                    targetDirectoryUri = archiveDirectoryUri,
                    onProgress = onProgress
                ).getOrNull() != null
            }
        }
    }

    /** 同根搬运：`DocumentsContract.moveDocument`，失败由调用方决定是否降级。 */
    private suspend fun moveDocumentFile(
        resolver: ContentResolver,
        sourceFileUri: Uri,
        sourceParentUri: Uri,
        targetName: String,
        targetDirectoryUri: Uri
    ): TaskResult<Uri> {
        return withContext(Dispatchers.IO) {
            safeNoNull {
                // 假设你已经有了源文件的 URI、源父目录 URI 和回收站目录 URI
                DocumentsContract.moveDocument(
                    resolver,
                    sourceFileUri,
                    sourceParentUri,
                    targetDirectoryUri
                )
            }.onFailure {
                log.e("moveDocumentFile failed", it)
            }
        }
    }

    /**
     * 跨根 / 降级搬运：创建目标文件 → 流式拷贝 → 删除源文件。
     *
     * 失败路径（创建失败 / 流打开失败 / 拷贝中断）都会清理目标侧的半成品，保证
     * **源文件原地不动**；进度经 [onProgress] 回调（粒度 1%）。
     */
    private suspend fun moveStreamFile(
        resolver: ContentResolver,
        sourceFileUri: Uri,
        targetName: String,
        targetDirectoryUri: Uri,
        onProgress: (Float) -> Unit
    ): TaskResult<Uri> {
        return withContext(Dispatchers.IO) { // 切换到 IO 线程执行
            safeNoNull {
                val totalSize = getFileSize(resolver, sourceFileUri).getOrNull() ?: 0L
                if (totalSize < 1) {
                    log.e("moveStreamFile, totalSize < 1, sourceUri = $sourceFileUri")
                    return@safeNoNull null
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
                    return@safeNoNull null
                }

                val finalName = findFileName(resolver, newFileUri).getOrNull() ?: ""

                if (finalName.isEmpty()) {
                    log.e("moveStreamFile, finalName == null, newFileUri = $newFileUri")
                    DocumentsContract.deleteDocument(resolver, newFileUri)
                    return@safeNoNull null
                }

                // 3. 使用 Kotlin 的 .use 扩展函数处理流，会自动 close
                val sourceStream = resolver.openInputStream(sourceFileUri)
                if (sourceStream == null) {
                    // 如果找不到源文件，那么就删除创建的文件
                    DocumentsContract.deleteDocument(resolver, newFileUri)
                    log.e("moveStreamFile, sourceStream == null, sourceUri = $sourceFileUri")
                    return@safeNoNull null
                }

                safeRun { sourceStream }.useNoNull { inputStream ->

                    val targetStream = resolver.openOutputStream(newFileUri)
                    if (targetStream == null) {
                        // 如果打不开源文件，就删除新文件，放弃吧
                        DocumentsContract.deleteDocument(resolver, newFileUri)
                        log.e("moveStreamFile, targetStream == null, newFileUri = $newFileUri")
                        return@useNoNull null
                    }

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
                    }
                }.onFailure {
                    DocumentsContract.deleteDocument(resolver, newFileUri)
                    log.e("moveStreamFile", it)
                }

                // 4. 拷贝成功后，删除原文件
                DocumentsContract.deleteDocument(resolver, sourceFileUri)

                newFileUri
            }.onFailure {
                log.e("moveStreamFile", it)
            }
        }
    }

    // ---------------------------------------------------------------------
    // 私有工具
    // ---------------------------------------------------------------------

    private suspend fun createNoMediaFile(context: Context, parentDocumentUri: Uri) {
        createFile(
            resolver = context.contentResolver,
            parentDocumentUri = parentDocumentUri,
            fileName = FILE_NO_MEDIA,
            mode = FileCreateMode.KeepOld
        )
    }

    private suspend fun readConfigByName(
        resolver: ContentResolver,
        treeUri: Uri,
        fileName: String
    ): TaskResult<Uri> {
        return queryFile(
            resolver = resolver,
            parentDocumentUri = treeUri,
            fileName = fileName
        )
    }

    private suspend fun findFileName(
        resolver: ContentResolver,
        fileUri: Uri
    ): TaskResult<String> {
        return withContext(Dispatchers.IO) {
            safeNoNull {
                val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                resolver.query(fileUri, projection, null, null, null)
            }.useNoNull { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                } else {
                    null
                }
            }.onFailure {
                log.e("findFileName", it)
            }
        }
    }

    private suspend fun queryFile(
        resolver: ContentResolver,
        parentDocumentUri: Uri,
        fileName: String
    ): TaskResult<Uri> {
        return withContext(Dispatchers.IO) {
            safeNoNull {
                // 1. 定向查询文件名，获取它的唯一 ID
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    parentDocumentUri,
                    DocumentsContract.getTreeDocumentId(parentDocumentUri)
                )

                val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                // 这里的 selection 就是 SQL 过滤条件
                val selection = "${DocumentsContract.Document.COLUMN_DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(fileName)

                resolver.query(
                    childrenUri,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )
            }.useNoNull { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else {
                    null
                }
            }.mapValue { docId ->
                DocumentsContract.buildDocumentUriUsingTree(
                    parentDocumentUri,
                    docId
                )
            }.onFailure {
                log.e("queryFile failed", it)
            }
        }
    }

    private suspend fun createFile(
        resolver: ContentResolver,
        parentDocumentUri: Uri,
        fileName: String,
        mode: FileCreateMode = FileCreateMode.DeleteOld
    ): TaskResult<Uri> {
        return withContext(Dispatchers.IO) {
            val oldUri = queryFile(
                resolver = resolver,
                parentDocumentUri = parentDocumentUri,
                fileName = fileName
            ).getOrNull()
            if (oldUri != null) {
                when (mode) {
                    FileCreateMode.DeleteOld -> {
                        // 2. 拿到精准 Uri 后，调用专门的删除函数
                        safeRun {
                            DocumentsContract.deleteDocument(resolver, oldUri)
                        }.onFailure {
                            log.e("createFile delete failed", it)
                        }
                    }

                    FileCreateMode.KeepOld -> {
                        return@withContext TaskResult.Success(oldUri)
                    }
                }
            }
            safeNoNull {
                // 直接通过 DocumentsContract 创建文档
                // 参数1: ContentResolver
                // 参数2: 父目录的 DocumentUri
                // 参数3: MIME 类型 (对于 .nomedia，通常用 application/octet-stream)
                // 参数4: 显示的文件名
                DocumentsContract.createDocument(
                    resolver,
                    parentDocumentUri,
                    "application/octet-stream",
                    fileName
                )
            }.onFailure {
                log.e("createFile failed", it)
            }
        }
    }

    /** 辅助函数：获取 SAF 文件大小 */
    private fun getFileSize(resolver: ContentResolver, uri: Uri): TaskResult<Long> {
        return safeNoNull {
            resolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_SIZE),
                null,
                null,
                null
            )
        }.useNoNull {
            if (it.moveToFirst()) {
                it.getLong(0)
            } else {
                0L
            }
        }.onFailure {
            log.e("getFileSize failed", it)
        }
    }

    private enum class FileCreateMode {
        DeleteOld,
        KeepOld
    }
}
