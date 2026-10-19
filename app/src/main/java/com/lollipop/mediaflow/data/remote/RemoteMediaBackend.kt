package com.lollipop.mediaflow.data.remote

import com.lollipop.mediaflow.data.source.MediaBackend
import com.lollipop.mediaflow.data.source.SourceId

/**
 * 远程来源扩展点（WebDAV / 后续协议）。
 *
 * 本期**只保留接口位置，不提供任何实现与数据**。
 *
 * 接入方式：实现本接口 → 在 [com.lollipop.mediaflow.data.source.MediaBackends] 中注册，
 * 视图层与 UI 无需改动即可自动接入；Public / Private 两套随 `visibility` 参数自动成立。
 */
interface RemoteMediaBackend : MediaBackend {

    /** 远程来源标识（协议 + 服务实例）。 */
    override val sourceId: SourceId.Remote

    /** 展示名（例如服务器备注名），用于远程资源行标题。 */
    val displayName: String
}
