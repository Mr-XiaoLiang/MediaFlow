package com.lollipop.mediaflow.data.source

import com.lollipop.mediaflow.data.local.LocalMediaBackend
import com.lollipop.mediaflow.data.local.MediaVisibility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 来源注册表。
 *
 * - [local]：本地来源，常驻；
 * - [remote]：远程来源（WebDAV 等）扩展点，本期**不提供任何实现与数据**，恒为空。
 *
 * 未来接入 WebDAV 时，实现 [MediaBackend]（可选地实现
 * [com.lollipop.mediaflow.data.remote.RemoteMediaBackend]）后在此注册即可，
 * 视图层与 UI 无需改动。
 */
object MediaBackends {

    /** 本地来源（唯一）。 */
    val local: MediaBackend = LocalMediaBackend

    /** 远程来源列表。预留位置，当前为空。 */
    fun remote(): List<MediaBackend> = emptyList()

    /**
     * 按 [SourceId] 定位来源后端，未注册返回 null。
     *
     * 供「以来源为参数」的入口（如播放页经 intent 携带的 [SourceId]）解析实际来源，
     * 避免调用方默认自己一定来自 [local]。
     */
    fun find(sourceId: SourceId): MediaBackend? {
        if (sourceId == local.sourceId) {
            return local
        }
        return remote().firstOrNull { it.sourceId == sourceId }
    }

    /** 按 (来源, 可见性) 取共享数据单元；来源未注册返回 null。 */
    fun catalog(sourceId: SourceId, visibility: MediaVisibility): MediaCatalog? {
        return find(sourceId)?.catalog(visibility)
    }

    /**
     * 视图物化的应用级作用域。
     *
     * 约定：[MediaView] 的投影（过滤 / 排序）在 [Dispatchers.Default] 执行，
     * 结果回到主线程推送；此作用域只负责"启动收集"，不承载计算。
     */
    internal val viewScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
