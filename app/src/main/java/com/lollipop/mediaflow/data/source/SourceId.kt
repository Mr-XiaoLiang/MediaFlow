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

    companion object {

        /** [Remote.key] 中协议与服务器标识的分隔符。 */
        private const val SCHEME_SEPARATOR = "://"

        /**
         * [key] 的解析入口，与各实现的 key 生成保持对称（可逆）。
         *
         * 用于跨进程 / 持久化边界（如 intent extra）往返：写入端传 [key]，
         * 读取端用本方法还原。空值或无法识别的 key 回退 [Local]。
         */
        fun parse(key: String?): SourceId {
            if (key.isNullOrEmpty() || key == Local.key) {
                return Local
            }
            val separator = key.indexOf(SCHEME_SEPARATOR)
            if (separator <= 0 || separator + SCHEME_SEPARATOR.length >= key.length) {
                return Local
            }
            return Remote(
                protocol = key.substring(0, separator),
                serverId = key.substring(separator + SCHEME_SEPARATOR.length)
            )
        }
    }
}
