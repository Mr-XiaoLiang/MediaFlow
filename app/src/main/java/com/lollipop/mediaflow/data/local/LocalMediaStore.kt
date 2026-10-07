package com.lollipop.mediaflow.data.local

import android.content.Context
import android.net.Uri
import com.lollipop.common.tools.DL
import com.lollipop.common.tools.LLog.Companion.registerLog
import com.lollipop.common.tools.TaskResult
import com.lollipop.common.tools.onFailure
import com.lollipop.common.tools.safeRun
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 本地数据源的第 3 层：按 [MediaVisibility] 隔离的存储门面。
 * Store 与 Cache 合并为同一层——本类直接持有并操作内存缓存，不另设中间 Cache 对象。
 * 底层不区分媒体类型 / 排序 / 范围，只有 visibility 一个维度。
 *
 * [Public] / [Private] 为两个独立 object 单例（全局仅 2 个），自带三个锁、缓存数据与版本号。
 * [context] 不保存，仅在 fill / refresh 调用时作为函数参数传入，用于扫盘与读库。
 *
 * 并发模型：fill 同类合并（同 visibility 只跑一次）、refresh 同类合并、fill ↔ refresh 经写锁排队。
 */
sealed class LocalMediaStore private constructor(val visibility: MediaVisibility) {

    companion object {
        fun from(visibility: MediaVisibility): LocalMediaStore {
            return when (visibility) {
                MediaVisibility.Public -> Public
                MediaVisibility.Private -> Private
            }
        }
    }

    val key: String = visibility.key

    /** 原始数据版本号：仅当底层原始缓存真正变化时递增，语义纯粹——只代表原始数据变化。 */
    @Volatile
    var dataVersion = 1L
        private set

    private val rootUriList = CopyOnWriteArrayList<RootUri>()
    private val rootUriMap = ConcurrentHashMap<String, RootUri>()

    private val allFileList = CopyOnWriteArrayList<MediaRoot>()
    private val directoryTree = CopyOnWriteArrayList<MediaDirectoryTree>()

    val rootList: List<RootUri>
        get() = rootUriList

    val fileList: List<MediaRoot>
        get() = allFileList

    val treeList: List<MediaDirectoryTree>
        get() = directoryTree

    // —— 三层锁（单例级 = 按 visibility 隔离）——
    /** fill 同类合并：同 visibility 的并发 fill 只跑一次，其余复用结果。 */
    private val fillMutex = Mutex()

    /** refresh（扫盘）同类合并：同 visibility 的并发 refresh 只跑一次。 */
    private val scanMutex = Mutex()

    /** 写锁：所有对内存缓存 / 数据库的写入都串行化，保证请求与返回的完整。 */
    private val writeMutex = Mutex()

    /** 按 key 缓存正在进行的 fill 协程（同 visibility 下按调用入口合并）。 */
    private var fillJobs: Deferred<Data>? = null

    /** 按 key 缓存正在进行的 refresh 协程。 */
    private var scanJobs: Deferred<Data>? = null

    /** 控制层私有协程作用域（加载失败不应取消其它加载）。 */
    private val scope = CoroutineScope(SupervisorJob())

    private val log by lazy {
        registerLog()
    }

    /** 填充入口：同类合并，内存优先、缺失则从数据库读。context 仅用于读取数据库。 */
    suspend fun fill(context: Context): Data {
        if (!fillMutex.tryLock()) {
            return fillJobs!!.await()
        }
        try {
            val job = scope.async { loadFromMemoryOrDb(context) }
            fillJobs = job
            return job.await()
        } finally {
            fillMutex.unlock()
        }
    }

    /** 刷新入口：同类合并，必扫盘并写回内存与数据库。context 用于扫盘与读库。 */
    suspend fun refresh(context: Context): Data {
        if (!scanMutex.tryLock()) {
            return scanJobs!!.await()
        }
        try {
            val job = scope.async { scanMedia(context) }
            scanJobs = job
            return job.await()
        } finally {
            scanMutex.unlock()
        }
    }

    /** 从内存或数据库填充（不扫盘）。 */
    private suspend fun loadFromMemoryOrDb(context: Context): Data {
        val cached = snapshot()
        if (!cached.isEmpty) {
            return cached
        }
        val localResult = LocalMediaProvider.fetchAllCache(
            visibility = visibility,
            db = LocalMediaLoader.getMediaDatabase(context)
        )
        val roots = ArrayList<MediaRoot>()
        val trees = ArrayList<MediaDirectoryTree>()
        rootList.forEach { root ->
            val rootChildren = ArrayList<MediaInfo>()
            localResult.top.forEach { top ->
                if (top.path == root.name) {
                    rootChildren.add(top)
                }
            }
            val mediaRoot = MediaRoot(root.name, rootChildren)
            roots.add(mediaRoot)
            trees.add(loadDirectoryTree(mediaRoot))
        }
        return resetData(tree = trees, rootUri = roots)
    }

    /** 扫盘并写回内存与数据库。 */
    private suspend fun scanMedia(context: Context): Data {
        return withContext(Dispatchers.IO) {
            loadRootSync(context)
            val roots = ArrayList<MediaRoot>()
            val trees = ArrayList<MediaDirectoryTree>()
            rootList.forEach {
                val mediaRoot = LocalMediaLoader.loadTreeSync(context, it.uri, it.name)
                roots.add(mediaRoot)
                trees.add(loadDirectoryTree(mediaRoot))
            }
            val allCount = LocalMediaProvider.save(
                visibility = visibility,
                db = LocalMediaLoader.getMediaDatabase(context),
                fileList = roots
            )
            DL.i("scanMedia 保存本地缓存完成，数据量 = $allCount")
            resetData(tree = trees, rootUri = roots)
        }
    }

    suspend fun add(context: Context, uri: Uri): TaskResult<Unit> {
        return withContext(Dispatchers.IO) {
            safeRun {
                val db = LocalMediaLoader.getMediaDatabase(context)
                val name = LocalMediaLoader.getRootFolderName(context, uri)
                val rootUri = RootUri(uri = uri, visibility = visibility, name = name ?: "")

                val old = rootUriMap[rootUri.uriString]
                if (old != null) {
                    rootUriList.remove(old)
                }
                rootUriMap[rootUri.uriString] = rootUri
                rootUriList.add(rootUri)

                db.saveRootUri(rootUri)
                log.i("add uri = $uri, rootList.size = ${rootList.size}")
            }.onFailure {
                log.e("add", it)
            }
        }
    }

    suspend fun remove(context: Context, uri: Uri): TaskResult<Unit> {
        return withContext(Dispatchers.IO) {
            safeRun {
                val db = LocalMediaLoader.getMediaDatabase(context)
                val uriString = uri.toString()

                val remove = rootUriMap.remove(uriString)
                if (remove != null) {
                    rootUriList.remove(remove)
                }

                db.deleteRootUri(uriString, visibility)
                log.i("remove uri = $uri, rootList.size = ${rootList.size}")
            }.onFailure {
                log.e("remove", it)
            }
        }
    }

    suspend fun loadRootUri(context: Context): TaskResult<Unit> {
        return withContext(Dispatchers.IO) {
            safeRun {
                loadRootSync(context)
                log.i("loadRootUri 加载根目录成功: ${rootList.size}, visibility = ${visibility.key}")
            }.onFailure {
                log.e("loadRootUri", it)
            }
        }
    }

    private fun loadRootSync(context: Context): List<RootUri> {
        val rootUri =
            LocalMediaLoader.getMediaDatabase(context).loadRootUri(visibility = visibility)
        val uriSet = rootUri.map { it.uri }.toSet()
        val validUri = MediaChooser.findPermissionValid(context, uriSet)
        log.i("loadRootSync 加载根目录成功: ${rootUri.size}, visibility = ${visibility.key}")
        return if (validUri.size != uriSet.size) {
            log.w("load 部分URI权限无效")
            val newList = rootUri.filter { it.uri in validUri }
            resetRoots(newList)
            newList
        } else {
            resetRoots(rootUri)
            log.i("loadRootSync 所有URI权限有效")
            rootUri
        }
    }

    private fun updateDataVersion() {
        dataVersion++
        if (dataVersion == Long.MAX_VALUE) {
            dataVersion = Long.MIN_VALUE
        }
    }

    suspend fun removeFile(file: MediaInfo.File): TaskResult<Unit> {
        return withContext(Dispatchers.IO) {
            // 删除文件强制锁定
            writeMutex.withLock {
                safeRun {
                    val docId = file.docId
                    val pendingList = LinkedList<MediaInfo.Directory>()
                    var hasChange = false
                    fileList.forEach { root ->
                        val iterator = root.children.iterator()
                        while (iterator.hasNext()) {
                            val child = iterator.next()
                            if (child is MediaInfo.File && child.docId == docId) {
                                iterator.remove()
                                hasChange = true
                            } else if (child is MediaInfo.Directory) {
                                pendingList.add(child)
                            }
                        }
                    }

                    while (pendingList.isNotEmpty()) {
                        val first = pendingList.removeFirst()
                        val iterator = first.children.iterator()
                        while (iterator.hasNext()) {
                            val child = iterator.next()
                            if (child is MediaInfo.File && child.docId == docId) {
                                iterator.remove()
                            } else if (child is MediaInfo.Directory) {
                                pendingList.add(child)
                            }
                        }
                    }
                    treeList.forEach {
                        it.calculateFileCount()
                    }
                    if (hasChange) {
                        updateDataVersion()
                    }
                }
            }
        }
    }

    private suspend fun resetData(tree: List<MediaDirectoryTree>, rootUri: List<MediaRoot>): Data {
        writeMutex.withLock {
            directoryTree.clear()
            directoryTree.addAll(tree)
            allFileList.clear()
            allFileList.addAll(rootUri)
            updateDataVersion()
            return snapshot()
        }
    }

    private fun resetRoots(rootUri: List<RootUri>) {
        rootUriList.clear()
        rootUriList.addAll(rootUri)
        rootUriMap.clear()
        rootUri.forEach {
            rootUriMap[it.uriString] = it
        }
        updateDataVersion()
    }

    /**
     * 内存快照：已有缓存时返回 [Data]，否则返回 null（交由调用方决定是否读库）。
     * 只读，不加锁——写操作统一经由 writeMutex 串行化。
     */
    private fun snapshot(): Data {
        return Data(
            roots = ArrayList(allFileList),
            trees = ArrayList(directoryTree)
        )
    }

    private fun loadDirectoryTree(mediaRoot: MediaRoot): MediaDirectoryTree {
        val root = MediaDirectoryTree.Root(mediaRoot)
        val pending = LinkedList<MediaDirectoryTree>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val treeParent = pending.removeFirst()
            if (treeParent is MediaDirectoryTree.Root) {
                treeParent.current.children.forEach { media ->
                    if (media is MediaInfo.Directory) {
                        val directory = MediaDirectoryTree.Directory(media, treeParent)
                        treeParent.children.add(directory)
                        pending.add(directory)
                    }
                }
            } else if (treeParent is MediaDirectoryTree.Directory) {
                treeParent.current.children.forEach { media ->
                    if (media is MediaInfo.Directory) {
                        val directory = MediaDirectoryTree.Directory(media, treeParent)
                        treeParent.children.add(directory)
                        pending.add(directory)
                    }
                }
            }
        }
        root.calculateFileCount()
        return root
    }

    /**
     * 一次加载的产出：底层缓存的原始数据集合（未做媒体类型 / 排序 / 范围筛选）。
     * 由各层在拿到后自行过滤，同一 item 不同筛选集合互不干扰。
     */
    data class Data(
        val roots: List<MediaRoot>,
        val trees: List<MediaDirectoryTree>
    ) {
        val isEmpty: Boolean = roots.isEmpty() && trees.isEmpty()
    }

    object Public : LocalMediaStore(MediaVisibility.Public)

    object Private : LocalMediaStore(MediaVisibility.Private)

}
