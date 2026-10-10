package com.lollipop.mediaflow.playback

import android.content.Context
import android.os.Looper
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.preload.PreCacheHelper
import com.lollipop.common.tools.LLog.Companion.registerLog
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

    /** 外圈索引 -> 已建立的 helper（含其 MediaItem，用于判断是否需要重建）。 */
    private val activeHelpers = HashMap<Int, Entry>()

    private data class Entry(val item: MediaItem, val helper: PreCacheHelper)

    /**
     * 以 [index] 为中心调度预缓存。
     *
     * 必须在主线程调用（[PreCacheHelper] 的回调线程即创建线程）。
     */
    @MainThread
    fun setCurrentIndex(index: Int, items: List<MediaItem>) {
        val targets = resolvePreCacheTargets(index, items)

        // 1) 回收：不在外圈、或该项已被替换的 helper
        val iterator = activeHelpers.iterator()
        while (iterator.hasNext()) {
            val (helperIndex, entry) = iterator.next()
            val target = targets[helperIndex]
            if (target == null || target != entry.item) {
                entry.helper.stop()
                // 只是归还调度权，保留已垫进 cache 的内容（回翻时还能命中）
                entry.helper.release(/* removeCachedContent= */ false)
                iterator.remove()
                log.i("release preCache: $helperIndex")
            }
        }

        // 2) 补齐：缺失的外圈项（本地文件跳过）
        targets.forEach { (targetIndex, item) ->
            if (activeHelpers.containsKey(targetIndex)) {
                return@forEach
            }
            val uri = item.localConfiguration?.uri ?: return@forEach
            if (SchemeDataSource.isLocalScheme(uri)) {
                return@forEach
            }
            val helper = helperFactory.create(item)
            helper.preCache(PreCacheStartMs, PRELOAD_DURATION_MS)
            activeHelpers[targetIndex] = Entry(item, helper)
            log.i("preCache: $targetIndex")
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

    /** 外圈目标：`{i-2, i+2}`，内圈（|offset| < 半径）交给保活 player。 */
    private fun resolvePreCacheTargets(index: Int, items: List<MediaItem>): Map<Int, MediaItem> {
        val targets = HashMap<Int, MediaItem>(PreCacheRadius)
        for (offset in -PreCacheRadius..PreCacheRadius) {
            if (abs(offset) < PreCacheRadius) {
                continue
            }
            val targetIndex = index + offset
            if (targetIndex in items.indices) {
                targets[targetIndex] = items[targetIndex]
            }
        }
        return targets
    }
}
