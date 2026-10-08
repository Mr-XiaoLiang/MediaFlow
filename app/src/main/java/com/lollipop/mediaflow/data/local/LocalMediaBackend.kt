package com.lollipop.mediaflow.data.local

import android.content.Context
import com.lollipop.mediaflow.data.LMedia
import com.lollipop.mediaflow.data.source.MediaBackend
import com.lollipop.mediaflow.data.source.MediaCatalog
import com.lollipop.mediaflow.data.source.SourceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.LinkedList

/**
 * Local 来源实现（来源轴）。
 *
 * 复用既有的 [LocalMediaStore]（分区 = (Local, visibility)）作为扫描 / 缓存层，
 * 只在其上做一件事：把原始数据拍平（含全部 mediaType）后发布进共享 [MediaCatalog]。
 *
 * ## 线程约定
 * - 读取 / 扫描：[LocalMediaStore] 内部已切 IO；
 * - 拍平等耗时操作：本类显式切 [Dispatchers.IO]；
 * - 发布：[MediaCatalog.publish] 内部固定切 Main。
 */
object LocalMediaBackend : MediaBackend {

    override val sourceId: SourceId = SourceId.Local

    private val catalogs = HashMap<MediaVisibility, MediaCatalog>()

    override fun catalog(visibility: MediaVisibility): MediaCatalog {
        return catalogs.getOrPut(visibility) {
            MediaCatalog(sourceId = sourceId, visibility = visibility)
        }
    }

    override suspend fun fill(context: Context, visibility: MediaVisibility) {
        val store = LocalMediaStore.from(visibility)
        val data = store.fill(context)
        publish(visibility, data, store.dataVersion)
    }

    override suspend fun refresh(context: Context, visibility: MediaVisibility) {
        val store = LocalMediaStore.from(visibility)
        val data = store.refresh(context)
        publish(visibility, data, store.dataVersion)
    }

    override suspend fun loadMore(context: Context, visibility: MediaVisibility) {
        // Local 无分页，保持空实现。
    }

    override suspend fun remove(
        context: Context,
        visibility: MediaVisibility,
        item: LMedia
    ) {
        val file = item as? MediaInfo.File ?: return
        val store = LocalMediaStore.from(visibility)
        // 删除（IO）→ 重新投影 → 发布（Main）。删除无结果观察方，仅需保证写库在 IO。
        withContext(Dispatchers.IO) { store.removeFile(file) }
        val data = store.fill(context)
        publish(visibility, data, store.dataVersion)
    }

    /** 耗时的拍平显式切 IO；发布由 [MediaCatalog.publish] 固定切回 Main。 */
    private suspend fun publish(
        visibility: MediaVisibility,
        data: LocalMediaStore.Data,
        version: Long
    ) {
        val items = withContext(Dispatchers.IO) { flatten(data) }
        catalog(visibility).publish(items = items, trees = data.trees, version = version)
    }

    /** 把带层级的 roots 拍平成文件列表（含全部 mediaType，供各视图自行筛选）。 */
    private fun flatten(data: LocalMediaStore.Data): List<LMedia> {
        val result = ArrayList<LMedia>()
        val pending = LinkedList<MediaInfo>()
        data.roots.forEach { pending.addAll(it.children) }
        while (pending.isNotEmpty()) {
            when (val item = pending.removeFirst()) {
                is MediaInfo.File -> result.add(item)
                is MediaInfo.Directory -> pending.addAll(item.children)
            }
        }
        return result
    }
}
