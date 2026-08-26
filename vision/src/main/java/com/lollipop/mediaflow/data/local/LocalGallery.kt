package com.lollipop.mediaflow.data.local

import android.content.Context
import com.lollipop.common.tools.LLog.Companion.registerLog
import com.lollipop.common.tools.TaskResult
import com.lollipop.common.tools.safeRun
import com.lollipop.mediaflow.data.common.MediaSort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.LinkedList

/**
 * 本地数据源的第 2 层：按 (visibility, mediaType) 分化的画廊。
 * 读共享的 [LocalMediaStore] 缓存，按 scopeId 选范围、按 sort 排序，得到独立的投影集合。
 *
 * 并发模型（与 [LocalMediaStore] 同构）：
 * - fill 同类（同 sort+scopeId）合并、refresh 同类合并；
 * - fill ↔ refresh 经写锁排队，不交叉。
 * 此外 fill 带短路：若请求参数与上次投影一致、且底层缓存版本未变，则直接复用已有投影。
 * [context] 不保存，仅在下发给 [LocalMediaStore.fill] / [LocalMediaStore.refresh] 时传入。
 */
class LocalGallery private constructor(
    val store: LocalMediaStore,
    val mediaType: MediaType,
    val visibility: MediaVisibility
) {

    companion object {

        val publicImage by lazy {
            createGallery(
                visibility = MediaVisibility.Public,
                mediaType = MediaType.Image
            )
        }
        val publicVideo by lazy {
            createGallery(
                visibility = MediaVisibility.Public,
                mediaType = MediaType.Video
            )
        }
        val privateImage by lazy {
            createGallery(
                visibility = MediaVisibility.Private,
                mediaType = MediaType.Image
            )
        }
        val privateVideo by lazy {
            createGallery(
                visibility = MediaVisibility.Private,
                mediaType = MediaType.Video
            )
        }

        private fun createGallery(visibility: MediaVisibility, mediaType: MediaType): LocalGallery {
            return LocalGallery(
                store = LocalMediaStore.from(visibility),
                mediaType = mediaType,
                visibility = visibility
            )
        }

        fun opt(visibility: MediaVisibility, mediaType: MediaType): LocalGallery {
            return when (visibility) {
                MediaVisibility.Public -> {
                    when (mediaType) {
                        MediaType.Image -> {
                            publicImage
                        }

                        MediaType.Video -> {
                            publicVideo
                        }
                    }
                }

                MediaVisibility.Private -> {
                    when (mediaType) {
                        MediaType.Image -> {
                            privateImage
                        }

                        MediaType.Video -> {
                            privateVideo
                        }
                    }
                }
            }
        }

    }

    private val directoryTree = ArrayList<MediaDirectoryTree>()

    private val fileList = ArrayList<MediaInfo.File>()

    /**
     * 范围筛选记录（choose 范围）。只读，仅随请求参数写入，供 UI 读取当前选中文件夹。
     */
    var rootDirectoryId: String = ""
        private set

    /**
     * 排序记录。只读，仅随请求参数写入，供 UI 读取当前排序。
     */
    var sortType: MediaSort = MediaSort.DateDesc
        private set

    /** 上次投影真实使用的排序 / 范围，以及当时底层缓存版本，用于 fill 短路判定。 */
    @Volatile
    private var lastSort: MediaSort? = null

    @Volatile
    private var lastScopeId: String? = null

    @Volatile
    private var lastStoreVersion: Long = -1

    private val log by lazy {
        registerLog()
    }

    /**
     * 一次投影的产出：按 [sort] / [scopeId] 从底层 [LocalMediaStore.Data] 过滤后的结果集合。
     */
    data class Snapshot(
        val files: List<MediaInfo.File>,
        val trees: List<MediaDirectoryTree>
    )

    // —— 三层锁（与 LocalMediaStore 同构）——
    /** fill 同类合并：同 Gallery 的并发 fill 只跑一次，其余复用结果。 */
    private val fillMutex = Mutex()

    /** refresh 同类合并：同 Gallery 的并发 refresh 只跑一次。 */
    private val scanMutex = Mutex()

    /** 写锁：保护自身投影结果（fileList / directoryTree）的写入，fill ↔ refresh 排队不交叉。 */
    private val writeMutex = Mutex()

    @Volatile
    private var fillJobs: Deferred<Snapshot>? = null

    @Volatile
    private var scanJobs: Deferred<Snapshot>? = null
    private val scope = CoroutineScope(SupervisorJob())

    /** fill 投影：读底层缓存（内存优先 / 数据库兜底），按参数过滤（必要时短路）。 */
    suspend fun fill(context: Context, sort: MediaSort, scopeId: String): TaskResult<Snapshot> {
        // 短路：参数一致且底层缓存版本未变，直接复用已有投影，避免无谓的重新过滤。
        if (lastSort == sort
            && lastScopeId == scopeId
            && lastStoreVersion == store.dataVersion
            && fileList.isNotEmpty()
        ) {
            return TaskResult.Success(
                Snapshot(
                    files = ArrayList(fileList),
                    trees = ArrayList(directoryTree)
                )
            )
        }
        if (!fillMutex.tryLock()) {
            return safeRun { fillJobs!!.await() }
        }
        try {
            return safeRun {
                val job = scope.async { project(context, sort, scopeId, isRefresh = false) }
                fillJobs = job
                job.await()
            }
        } finally {
            fillMutex.unlock()
        }
    }

    /** refresh 投影：底层扫盘后，按参数过滤（必扫盘，不做短路）。 */
    suspend fun refresh(context: Context, sort: MediaSort, scopeId: String): TaskResult<Snapshot> {
        if (!scanMutex.tryLock()) {
            return safeRun { scanJobs!!.await() }
        }
        try {
            return safeRun {
                val job = scope.async { project(context, sort, scopeId, isRefresh = true) }
                scanJobs = job
                job.await()
            }
        } finally {
            scanMutex.unlock()
        }
    }

    /**
     * 投影核心：从底层拿到 [LocalMediaStore.Data]（fill 走缓存、refresh 走扫盘），按 scopeId 选范围、
     * 按 sort 排序，得到与本次参数对应的独立集合；写入自身的投影结果集合供 UI 读取，
     * 并记录真实参数与底层缓存版本，供后续 fill 短路判定。
     */
    private suspend fun project(
        context: Context,
        sort: MediaSort,
        scopeId: String,
        isRefresh: Boolean
    ): Snapshot {
        val data = if (isRefresh) {
            store.refresh(context)
        } else {
            store.fill(context)
        }

        lastSort = sort
        lastScopeId = scopeId
        lastStoreVersion = store.dataVersion

        // 记录本次真实使用的参数（对外只读，供上层并发后校准）。
        this.sortType = sort
        this.rootDirectoryId = scopeId

        val tempTree = ArrayList<MediaDirectoryTree>(data.trees)
        val allFile = ArrayList<MediaInfo.File>()

        val dirTree = findDirTree(tempTree, scopeId)
        if (dirTree != null) {
            loadFromDirectory(dirTree, allFile)
        } else {
            loadAll(data.roots, allFile)
        }
        sort.sort(allFile)

        writeMutex.withLock {
            fileList.clear()
            fileList.addAll(allFile)
            directoryTree.clear()
            directoryTree.addAll(tempTree)
        }
        return Snapshot(files = ArrayList(allFile), trees = ArrayList(tempTree))
    }

    suspend fun remove(info: MediaInfo.File): TaskResult<Unit> {
        fileList.remove(info)
        return store.removeFile(info)
    }

    private fun loadAll(pending: LinkedList<MediaInfo>, out: MutableList<MediaInfo.File>) {
        log.i("loadAll pending.size = ${pending.size}")
        while (pending.isNotEmpty()) {
            val item = pending.removeFirst()
            if (item is MediaInfo.File) {
                if (item.mediaType == mediaType) {
                    out.add(item)
                }
                continue
            }
            if (item is MediaInfo.Directory) {
                item.children.forEach { child ->
                    if (child is MediaInfo.File) {
                        if (child.mediaType == mediaType) {
                            out.add(child)
                        }
                    } else if (child is MediaInfo.Directory) {
                        pending.add(child)
                    }
                }
            }
        }
    }

    private fun loadFromDirectory(
        dir: MediaDirectoryTree,
        out: MutableList<MediaInfo.File>
    ) {
        log.i("loadFromDirectory dir = ${dir.name}")
        val pending = LinkedList<MediaInfo>()
        if (dir is MediaDirectoryTree.Root) {
            pending.addAll(dir.current.children)
        } else if (dir is MediaDirectoryTree.Directory) {
            pending.addAll(dir.current.children)
        }
        loadAll(pending, out)
    }

    private fun loadAll(rootList: List<MediaRoot>, out: MutableList<MediaInfo.File>) {
        log.i("loadAll rootList.size = ${rootList.size}")
        val pending = LinkedList<MediaInfo>()
        rootList.forEach {
            pending.addAll(it.children)
        }
        loadAll(pending, out)
    }

    private fun findDirTree(
        allTree: List<MediaDirectoryTree>,
        scopeId: String
    ): MediaDirectoryTree? {
        if (scopeId.isEmpty()) {
            return null
        }
        val pending = LinkedList<MediaDirectoryTree>()
        pending.addAll(allTree)
        while (pending.isNotEmpty()) {
            val item = pending.removeFirst()
            if (item.id == scopeId) {
                return item
            }
            pending.addAll(item.children)
        }
        return null
    }

}
