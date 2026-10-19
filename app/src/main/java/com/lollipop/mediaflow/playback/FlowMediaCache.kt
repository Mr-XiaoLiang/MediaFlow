package com.lollipop.mediaflow.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * App 级媒体缓存（进程内单例的 [SimpleCache]）。
 *
 * 对齐计划第三节「分层 / 缓存与去重」：
 * - 同一 `cacheDir` 只能存在**一个** [SimpleCache] 实例，因此这里做进程内单例；
 * - 淘汰策略为 [LeastRecentlyUsedCacheEvictor]，体积上限取自
 *   [FlowPreloadController.CACHE_MAX_BYTES]（按计划：两个维度的常量集中在预缓存控制器，调试期只改一处）；
 * - 所有 player 的 `MediaSource.Factory` 与 `PreCacheHelper` **共用同一个 Cache**；
 * - [CacheDataSource] 开启 [CacheDataSource.FLAG_BLOCK_ON_CACHE]：不开时若某 key 正被写锁，
 *   会回上游读，同一次加载会走两遍网络。
 */
@OptIn(UnstableApi::class)
object FlowMediaCache {

    private const val CacheDirName = "flow_media_cache"

    private var cache: SimpleCache? = null

    /**
     * 缓存索引数据库。
     *
     * 无参 `SimpleCache(dir, evictor)` 构造已废弃（走 legacy 索引、性能差），
     * 官方推荐传入 [StandaloneDatabaseProvider]；它同样只应有一个实例，故与 cache 一起进程内复用。
     */
    private var databaseProvider: StandaloneDatabaseProvider? = null

    /** 取（或首次创建）App 级缓存实例。 */
    @Synchronized
    fun get(context: Context): SimpleCache {
        cache?.let { return it }
        val applicationContext = context.applicationContext
        val directory = File(applicationContext.cacheDir, CacheDirName)
        val evictor = LeastRecentlyUsedCacheEvictor(FlowPreloadController.CACHE_MAX_BYTES)
        val provider = databaseProvider ?: StandaloneDatabaseProvider(applicationContext).also {
            databaseProvider = it
        }
        return SimpleCache(directory, evictor, provider).also { cache = it }
    }

    /**
     * 播放器侧数据源工厂：读缓存 + 回写缓存，上游为 [DefaultDataSource]（网络 / 本地通吃）。
     *
     * 注意：是否需要写缓存由 [SchemeDataSource] 按 scheme 决定（本地文件不写）。
     */
    @Synchronized
    fun cacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(get(context))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context))
            .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE)
    }

    /** 释放缓存（进程退出 / 需要重建时调用）。 */
    @Synchronized
    fun release() {
        cache?.release()
        cache = null
    }
}
