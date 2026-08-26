package com.lollipop.mediaflow.data

import android.content.Context
import androidx.compose.runtime.snapshots.Snapshot
import com.lollipop.common.tools.TaskResult
import com.lollipop.mediaflow.data.local.LocalGallery
import com.lollipop.mediaflow.data.local.LocalState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.util.concurrent.ConcurrentHashMap

/**
 * 控制层：负责实际数据加载与缓存。
 *
 * 密封类，内部只控制有限实现（Local / WebDAV 等），所有实现一眼可见。
 * 具体包装业务（如何读缓存、如何投影）交由各来源实现，这里只做编排。
 *
 * 每次请求都传入一个 [SourceState]（如 [LocalState.PublicVideo]），
 * 控制层通过它反解要刷新的范围与要填充的列表，并同步加载 / 错误状态。
 *
 * 并发控制集中在最外层：数据操作类（MediaStore / Gallery）只保证自身纯净的
 * 挂起返回，不内部节流；重复的 refresh 由本控制层做「单飞」合并（短时间内的
 * 无用请求只跑一次真实扫描，但每个调用方都各自拿到返回）。
 */
sealed class SourceLoader {

    /**
     * 把当前已缓存的数据投影进展示列表，不做任何加载（快）。
     * 按 [SourceState.scopeId] 决定「全部」还是「只某范围」（choose）。
     */
    abstract suspend fun fill(context: Context, state: SourceState)

    /**
     * 触发一次完整刷新：重扫整 visibility 缓存（较慢），筛选后投影进
     * 对应展示列表，并同步 [SourceState] 的加载 / 错误状态。
     */
    abstract suspend fun refresh(context: Context, state: SourceState)

    /**
     * 触发分页 / 增量加载更多。
     * Local 不做分页，所以 Local 的实现为空。（WebDAV 等有懒加载分层的来源才需要实现。）
     */
    abstract suspend fun loadMore(context: Context, state: SourceState)

    /**
     * Local 实现：就近写在密封类内。
     * 读取缓存（MediaStore.Gallery）→ 按 scopeId / sort 筛选 → 投影进 MediaSource.local。
     */
    object Local : SourceLoader() {

        /**
         * SourceState 级抖动合并：同一 state 的并发 refresh 复用同一扫描，
         * 每个调用方各自 await 返回。真正的数据层锁在 Gallery / MediaStore 内部。
         */
        private val refreshJobs = ConcurrentHashMap<SourceState, Job>()

        /** 控制层私有协程作用域（刷新失败不应取消其它加载）。 */
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        /** 把 Gallery 本次投影结果落地进对应 MediaSource 列表。 */
        private fun fillInto(source: MediaSource, result: TaskResult<LocalGallery.Snapshot>) {
            source.local.apply {
                Snapshot.withMutableSnapshot {
                    clear()
                    result.getOrNull()?.let {
                        addAll(it.files)
                    }
                }
            }
        }

        /**
         * 反向校准：用 Gallery 本次真实使用的参数覆盖 state，纠正并发导致的参数错位。
         */
        private fun calibrate(state: SourceState, gallery: LocalGallery) {
            state.setSort(gallery.sortType)
            state.setScopeId(gallery.rootDirectoryId)
        }

        override suspend fun fill(context: Context, state: SourceState) {
            val gallery = LocalGallery.opt(state.visibility, state.mediaType)
            val result = gallery.fill(context, state.sort.value, state.scopeId.value)
            fillInto(MediaSource.of(state.visibility, state.mediaType), result)
            calibrate(state, gallery)
        }

        override suspend fun refresh(context: Context, state: SourceState) {
            val existing = refreshJobs[state]
            if (existing != null && existing.isActive) {
                // 抖动合并：复用同一扫描，但本调用方也要等它结束后再校准。
                existing.join()
                return
            }
            val job: Job = scope.async {
                runCatching {
                    state.setLoading(true)
                    state.setError(null)
                    val gallery = LocalGallery.opt(state.visibility, state.mediaType)
                    val result = gallery.refresh(context, state.sort.value, state.scopeId.value)
                    fillInto(MediaSource.of(state.visibility, state.mediaType), result)
                    calibrate(state, gallery)
                }.onFailure {
                    state.setError(it)
                }.also {
                    state.setLoading(false)
                }
                Unit
            }
            refreshJobs[state] = job
            runCatching { job.join() }
            refreshJobs.remove(state)
        }

        override suspend fun loadMore(context: Context, state: SourceState) {
            // Local 无分页，空实现。
        }
    }
}
