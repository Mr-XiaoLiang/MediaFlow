package com.lollipop.mediaflow.data.source

import com.lollipop.mediaflow.data.local.LocalMediaBackend
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
     * 视图物化的应用级作用域。
     *
     * 约定：[MediaView] 的投影（过滤 / 排序）在 [Dispatchers.Default] 执行，
     * 结果回到主线程推送；此作用域只负责"启动收集"，不承载计算。
     */
    internal val viewScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
