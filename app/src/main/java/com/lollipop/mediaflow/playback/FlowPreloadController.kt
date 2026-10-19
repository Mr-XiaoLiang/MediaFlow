package com.lollipop.mediaflow.playback

import android.content.Context
import android.os.Looper
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.preload.PreCacheHelper
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import kotlin.math.abs

/**
 * Activity 级预缓存调度器（持 [PreCacheHelper]）。
 *
 * ## 窗口协议（对齐计划第三节「预加载窗口」）
 * 以当前项 `i` 为中心：
 * - **Player 窗口 ±1**：由保活页自身的 `prepare` 覆盖，内圈**不**额外预加载；
 * - **预缓存窗口 ±2**：只有外圈 `{i-2, i+2}` 交给 [PreCacheHelper]；
 * - 索引变化时：不在外圈的 helper 执行 `stop()` + `release(removeCachedContent = false)`，
 *   缺失的补 `create(item).preCache(0, PRELOAD_DURATION_MS)`。
 *
 * ## 常量
 * 计划要求两个维度各自独立、集中在本类，调试期只改这里：
 * - [CACHE_MAX_BYTES]：体积维度，[FlowMediaCache] 的 Cache 上限；
 * - [PRELOAD_DURATION_MS]：时长维度，`PreCacheHelper.preCache` 的预取时长。
 *
 * ## 边界
 * 本地文件（`file` / `content` 等）**不做**预缓存：预缓存本质是把起始段垫进磁盘 cache，
 * 对本地文件没有收益，只会白占空间与被 LRU 淘汰。
 */
@OptIn(UnstableApi::class)
class FlowPreloadController(private val context: Context) {

    companion object {

        /** 体积维度：Cache 上限。 */
        const val CACHE_MAX_BYTES = 70L * 1024 * 1024

        /** 时长维度：单次预取时长。 */
        const val PRELOAD_DURATION_MS = 20_000L

        /** 预缓存外圈半径：窗口 ±2，内圈 ±1 由保活 player 覆盖。 */
        private const val PreCacheRadius = 2

        /** 预取起始位置：从起始段开始垫。 */
        private const val PreCacheStartMs = 0L
    }

    private val log by lazy {
        registerLog()
    }

    private val helperFactory = PreCacheHelper.Factory(
        context.applicationContext,
        FlowMediaCache.get(context),
        DefaultDataSource.Factory(context),
        Looper.getMainLooper()
    )

    /** 外圈**身份**（MediaItem 的 uri）-> 已建立的 helper（含其 MediaItem，用于判断是否需要重建）。 */
    private val activeHelpers = HashMap<String, Entry>()

    private data class Entry(val item: MediaItem, val helper: PreCacheHelper)

    /**
     * 以 [index] 为中心调度预缓存。
     *
     * 必须在主线程调用（[PreCacheHelper] 的回调线程即创建线程）。
     */
    @MainThread
    fun setCurrentIndex(index: Int, items: List<MediaItem>) {
        val targets = resolvePreCacheTargets(index, items)

        // 1) 回收：不在外圈、或该项已被替换的 helper（按**身份**比对，与列表下标无关）
        val iterator = activeHelpers.iterator()
        while (iterator.hasNext()) {
            val (identity, entry) = iterator.next()
            val target = targets[identity]
            if (target == null || target != entry.item) {
                entry.helper.stop()
                // 只是归还调度权，保留已垫进 cache 的内容（回翻时还能命中）
                entry.helper.release(/* removeCachedContent= */ false)
                iterator.remove()
                log.i("release preCache: $identity")
            }
        }

        // 2) 补齐：缺失的外圈项（本地文件跳过）
        targets.forEach { (identity, item) ->
            if (activeHelpers.containsKey(identity)) {
                return@forEach
            }
            val uri = item.localConfiguration?.uri ?: return@forEach
            if (SchemeDataSource.isLocalScheme(uri)) {
                return@forEach
            }
            val helper = helperFactory.create(item)
            helper.preCache(PreCacheStartMs, PRELOAD_DURATION_MS)
            activeHelpers[identity] = Entry(item, helper)
            log.i("preCache: $identity")
        }
    }

    /** 释放全部 helper（Activity 销毁时调用）。 */
    @MainThread
    fun release() {
        activeHelpers.values.forEach {
            it.helper.stop()
            it.helper.release(/* removeCachedContent= */ false)
        }
        activeHelpers.clear()
    }

    /**
     * 外圈目标：`{i-2, i+2}`，内圈（|offset| < 半径）交给保活 player。
     *
     * 以**身份**（MediaItem 的 uri）为 key——下标会随列表变化（归档 / 删除）漂移，
     * 身份不会，因此与 Pager 的 item remove 天然对齐。
     */
    private fun resolvePreCacheTargets(index: Int, items: List<MediaItem>): Map<String, MediaItem> {
        val targets = HashMap<String, MediaItem>(PreCacheRadius)
        for (offset in -PreCacheRadius..PreCacheRadius) {
            if (abs(offset) < PreCacheRadius) {
                continue
            }
            val targetIndex = index + offset
            if (targetIndex in items.indices) {
                val item = items[targetIndex]
                val identity = item.localConfiguration?.uri?.toString() ?: continue
                targets[identity] = item
            }
        }
        return targets
    }
}
