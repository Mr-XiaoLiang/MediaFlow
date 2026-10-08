package com.lollipop.mediaflow.data.source

/**
 * 数据来源的稳定标识（来源轴）。
 *
 * - [Local]：设备本地（MediaStore / SAF），全局唯一；
 * - [Remote]：远程来源（WebDAV 等），每个服务实例一个。
 *
 * 说明：可见性（Public / Private）不是来源的子属性，而是与来源并列的**全局维度**，
 * 因此不出现在这里，而是作为 [MediaCatalog] / [MediaQuery] 的参数存在。
 */
sealed interface SourceId {

    /** 供日志 / 缓存 key / 去重使用的稳定字符串。 */
    val key: String

    data object Local : SourceId {
        override val key: String = "local"
    }

    data class Remote(
        val protocol: String,
        val serverId: String
    ) : SourceId {
        override val key: String get() = "$protocol://$serverId"
    }
}
