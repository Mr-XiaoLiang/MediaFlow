package com.lollipop.mediaflow.data

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import com.lollipop.mediaflow.data.local.LocalState
import com.lollipop.mediaflow.data.local.MediaDirectoryTree
import com.lollipop.mediaflow.data.local.MediaType
import com.lollipop.mediaflow.data.local.MediaVisibility
import com.lollipop.mediaflow.data.source.MediaBackends
import com.lollipop.mediaflow.data.source.MediaCatalog
import com.lollipop.mediaflow.data.source.MediaQuery
import com.lollipop.mediaflow.data.source.MediaSnapshot
import com.lollipop.mediaflow.data.source.MediaView

/**
 * 面向 UI 的「展示来源组」：分区 = (visibility, mediaType)。
 *
 * 内部不再持有数据副本，而是持有一组 [MediaView]：
 * - local 视图从 (Local, visibility) 的共享 Catalog 派生；
 * - remote 视图从已注册的远程来源派生（本期恒为空，预留 WebDAV）。
 *
 * 参数（sort / scopeId）来自 [LocalState]，因此视频与图片各自独立；
 * 数据来自共享 Catalog，因此同源、只加载一份。
 *
 * UI 侧仍以普通 List 暴露，读取时订阅对应 State，触发按需重组。
 */
class MediaSource internal constructor(
    val visibility: MediaVisibility,
    val mediaType: MediaType
) {

    /** 本地来源的共享数据单元。 */
    private val localCatalog: MediaCatalog = MediaBackends.local.catalog(visibility)

    /** 远程来源的共享数据单元（本期为空，预留 WebDAV 等）。 */
    private val remoteCatalogs: List<MediaCatalog> = MediaBackends.remote().map { backend ->
        backend.catalog(visibility)
    }

    /** 本地视图：从 (Local, visibility) 的共享 Catalog 派生。 */
    private val localView: MediaView = MediaView(
        catalog = localCatalog,
        queryProvider = { currentQuery() },
        scope = MediaBackends.viewScope
    )

    /** 远程视图：本期为空，预留 WebDAV 等远程来源。 */
    private val remoteViews: List<MediaView> = remoteCatalogs.map { catalog ->
        MediaView(
            catalog = catalog,
            queryProvider = { currentQuery() },
            scope = MediaBackends.viewScope
        )
    }

    /** 本地展示列表。 */
    val local: List<LMedia> get() = localView.items.value

    /** 远程展示列表（本期恒为空）。 */
    val webDAV: List<LMedia> get() {
        if (remoteViews.isEmpty()) {
            return emptyList()
        }
        return remoteViews.flatMap { it.items.value }
    }

    /** 目录树（与 mediaType 无关，多视图共享同一份 Catalog 树）。 */
    val directoryTree: List<MediaDirectoryTree> get() = localView.directoryTree.value

    /**
     * 是否建议用户「添加来源或刷新」。
     *
     * 名下来源里只要有「加载过但没有数据」（[MediaSnapshot.Empty]）的，就建议用户干预；
     * 「还没有加载过」（[MediaSnapshot.Unloaded]）与「有数据」都属正常，不提示。
     */
    val suggestAddSourceOrRefresh: State<Boolean> = derivedStateOf {
        localCatalog.snapshot.value === MediaSnapshot.Empty ||
            remoteCatalogs.any { it.snapshot.value === MediaSnapshot.Empty }
    }

    private fun currentQuery(): MediaQuery {
        val state = LocalState.of(visibility, mediaType)
        return MediaQuery(
            mediaType = mediaType,
            sort = state.sort.value,
            scopeId = state.scopeId.value
        )
    }

    companion object {

        private val instances = HashMap<Key, MediaSource>()

        /** 按 (visibility, mediaType) 找到对应的展示来源组。 */
        fun of(visibility: MediaVisibility, mediaType: MediaType): MediaSource {
            return instances.getOrPut(Key(visibility, mediaType)) {
                MediaSource(visibility, mediaType)
            }
        }

        private data class Key(
            val visibility: MediaVisibility,
            val mediaType: MediaType
        )
    }
}
