package com.lollipop.mediaflow.playback

import android.net.Uri
import androidx.collection.LruCache

/**
 * 播放进度记忆（进程内 LRU 表）。
 *
 * ## 为什么用独立存储，而不是写回媒体模型
 * 播放进度是**播放期的临时状态**，不属于媒体的固有信息，因此不落到 `LMedia` / 数据集合上：
 * - **有上限**：[MAX_ENTRIES] 条，超出按最近最少使用自动淘汰，不会随浏览记录无限增长；
 * - **随进程消失**：不落盘，App 退出即清空，符合「会话内续播」的语义；
 * - **与来源无关**：键取 [Uri]（[com.lollipop.mediaflow.data.LMedia.uri]），
 *   Local / WebDAV / 未来来源一视同仁，上层无需感知任何具体类型。
 *
 * 仅在主线程读写（进度轮询与组合读取），`LruCache` 自身线程安全，这里不做额外同步。
 */
object PlaybackProgressStore {

    /** 容量上限（条数）：只影响「能记住多少个视频」，超限的旧条目会被自动淘汰。 */
    private const val MAX_ENTRIES = 100

    private val progressCache = LruCache<String, Long>(MAX_ENTRIES)

    /** 读取进度（毫秒）；无记录返回 0。 */
    fun get(uri: Uri): Long {
        return progressCache.get(uri.toString()) ?: 0L
    }

    /** 写入进度（毫秒）；`<= 0` 视为「无记忆」，直接移除。 */
    fun set(uri: Uri, positionMs: Long) {
        val key = uri.toString()
        if (positionMs > 0L) {
            progressCache.put(key, positionMs)
        } else {
            progressCache.remove(key)
        }
    }

    /** 清空全部记录（可选：例如需要显式回收时调用）。 */
    fun clear() {
        progressCache.evictAll()
    }
}
