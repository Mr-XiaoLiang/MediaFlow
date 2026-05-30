package com.lollipop.mediaflow.data

import android.content.Context
import androidx.collection.LruCache
import com.lollipop.common.tools.LLog.Companion.registerLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

object MetadataLoader {

    private val log by lazy {
        registerLog()
    }

    private const val CACHE_SIZE = 500 // 全局缓存可以稍大一点

    // 使用 Deferred 确保同一个 URI 无论在哪调用，都只触发一次真正的加载任务
    private val taskCache = object : LruCache<String, Deferred<MediaMetadata?>>(CACHE_SIZE) {
        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: Deferred<MediaMetadata?>,
            newValue: Deferred<MediaMetadata?>?
        ) {
            if (evicted) {
                oldValue.cancel()
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * 加载元数据。支持多处同时调用，会自动复用同一个加载任务。
     * @param info 媒体信息
     * @param onResult 回调结果（在主线程回调）
     * @return 返回 Job，调用方可根据生命周期自行 cancel
     */
    @JvmStatic
    fun load(context: Context, info: MediaInfo.File, onResult: (MediaMetadata?) -> Unit): Job? {
        val uri = info.uriString

        // 如果已经加载好了内存对象，直接回调
        info.metadata?.let {
            onResult(it)
            return null // 返回空 Job
        }

        return scope.launch {
            val deferred = synchronized(taskCache) {
                taskCache[uri] ?: async(Dispatchers.IO) {
                    try {
                        // 实际执行耗时加载
                        MediaLoader.loadMediaMetadataSync(context, info, false)
                        return@async info.metadata
                    } catch (e: Throwable) {
                        log.e("loadMetadataSync")
                        return@async null
                    }
                }.also {
                    taskCache.put(key = uri, value = it)
                }
            }

            try {
                val metadata = deferred.await()
                onResult(metadata)
            } catch (_: CancellationException) {
                // 仅取消当前的 launch 监听，不取消 Deferred 任务，因为可能有其他地方在等
            } catch (e: Exception) {
                log.e("Load failed: $uri", e)
                onResult(null)
            }
        }
    }

    /**
     * 提供一个预加载接口，不需要回调
     */
    fun preload(context: Context, info: MediaInfo.File) {
        load(context, info) { /* 仅触发加载入缓存 */ }
    }
}