package com.lollipop.mediaflow.data.source

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import com.lollipop.mediaflow.data.LMedia
import com.lollipop.mediaflow.data.local.MediaDirectoryTree
import com.lollipop.mediaflow.data.local.MediaInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.LinkedList

/**
 * Catalog 的一个投影视图（读模型）。
 *
 * ## 线程与耗时约定（写进结构，而非依赖调用方自觉）
 * - **投影计算**（按 scope 过滤 + 按 mediaType 过滤 + 排序）显式切到 [Dispatchers.IO] 执行；
 * - 结果回到收集方（Main）后才写入 [items] 的 State；
 * - 因此 **UI / 组合线程永不参与过滤与排序**，读取 [items] 只是读一个已算好的不可变列表。
 *
 * ## 触发与去重
 * 由 [snapshotFlow] 监听 `(Catalog 版本, Query 参数)`：
 * - 二者不变时不重算（`distinctUntilChanged`）——避免额外消耗，并保证 Random 排序稳定；
 * - Catalog 版本变化（扫描 / 删除）或参数变化（排序 / 范围）时才重新物化一次；
 * - [collectLatest] 保证快速连续变更时只保留最新一次投影结果。
 *
 * ## 独立性
 * 参数来自 [queryProvider]（各视图独立持有 sort / scope），数据来自共享 [catalog]，
 * 因此视频与图片互不干扰，却又同源。
 */
class MediaView(
    private val catalog: MediaCatalog,
    private val queryProvider: () -> MediaQuery,
    scope: CoroutineScope
) {

    private val itemsState = mutableStateOf<List<LMedia>>(emptyList())

    /** 展示列表：后台物化后推送，UI 线程只读。 */
    val items: State<List<LMedia>> get() = itemsState

    /** 目录树与 mediaType 无关，直接透传（无需计算）。 */
    val directoryTree: State<List<MediaDirectoryTree>> = derivedStateOf {
        catalog.snapshot.value.trees
    }

    init {
        scope.launch {
            snapshotFlow { ViewKey(catalog.snapshot.value, queryProvider()) }
                .distinctUntilChanged()
                .collectLatest { key ->
                    // 耗时投影显式切 IO；结果回到收集方（Main）再写回 State。
                    val result = withContext(Dispatchers.IO) {
                        project(key.snapshot, key.query)
                    }
                    itemsState.value = result
                }
        }
    }

    /** 去重键：Snapshot 由引用标识（版本变化才换实例），Query 由值标识。 */
    private data class ViewKey(
        val snapshot: MediaSnapshot,
        val query: MediaQuery
    )

    private fun project(snapshot: MediaSnapshot, query: MediaQuery): List<LMedia> {
        val scoped = filterByScope(snapshot, query.scopeId)
        val result = ArrayList<LMedia>(scoped.size)
        for (item in scoped) {
            if (item.mediaType == query.mediaType) {
                result.add(item)
            }
        }
        query.sort.sort(result)
        return result
    }

    /** 按范围（文件夹 / 目录）筛选；为空表示全部。 */
    private fun filterByScope(snapshot: MediaSnapshot, scopeId: String): List<LMedia> {
        if (scopeId.isEmpty()) {
            return snapshot.items
        }
        val target = findDirectory(snapshot.trees, scopeId) ?: return snapshot.items
        val result = ArrayList<LMedia>()
        val pending = LinkedList<MediaInfo>()
        when (target) {
            is MediaDirectoryTree.Root -> pending.addAll(target.current.children)
            is MediaDirectoryTree.Directory -> pending.addAll(target.current.children)
        }
        while (pending.isNotEmpty()) {
            when (val item = pending.removeFirst()) {
                is MediaInfo.File -> result.add(item)
                is MediaInfo.Directory -> pending.addAll(item.children)
            }
        }
        return result
    }

    private fun findDirectory(
        trees: List<MediaDirectoryTree>,
        scopeId: String
    ): MediaDirectoryTree? {
        val pending = LinkedList<MediaDirectoryTree>()
        pending.addAll(trees)
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
