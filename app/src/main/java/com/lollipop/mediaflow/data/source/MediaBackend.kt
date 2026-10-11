package com.lollipop.mediaflow.data.source

import android.content.Context
import com.lollipop.mediaflow.data.LMedia
import com.lollipop.mediaflow.data.local.MediaVisibility

/**
 * 统一数据源抽象（来源轴）。
 *
 * 一个后端实例 = 一条来源（[com.lollipop.mediaflow.data.local.LocalMediaBackend] / WebDAV / 后续协议）。
 * 来源活在（可见性）分组内，**实例自身不自述可见性**；可见性作为方法参数传入，
 * 与 [MediaCatalog] 的 (sourceId, visibility) 分区键一致。
 *
 * 契约约定：
 * - [catalog] 返回该 (source, visibility) 的共享数据单元，UI 侧视图从中派生；
 * - [fill] / [refresh] / [loadMore] 只负责更新 Catalog，不感知 UI；
 * - [remove] 是唯一写操作入口，写后由实现负责让 Catalog 失效重算。
 */
interface MediaBackend {

    /** 本后端的来源标识。 */
    val sourceId: SourceId

    /** 取某个可见性的共享数据单元（同一实例在生命周期内复用）。 */
    fun catalog(visibility: MediaVisibility): MediaCatalog

    /** 从缓存投影（快），不触发扫描。 */
    suspend fun fill(context: Context, visibility: MediaVisibility)

    /** 完整刷新（扫描 / 拉取）。 */
    suspend fun refresh(context: Context, visibility: MediaVisibility)

    /** 分页 / 增量加载。不分页的来源使用默认空实现。 */
    suspend fun loadMore(context: Context, visibility: MediaVisibility) {
        // 默认不分页
    }

    /** 移除某项（归档 / 删除）。 */
    suspend fun remove(context: Context, visibility: MediaVisibility, item: LMedia)
}
