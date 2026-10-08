package com.lollipop.mediaflow.data

import android.content.Context
import com.lollipop.common.tools.onFailure
import com.lollipop.mediaflow.data.local.LocalMediaBackend
import com.lollipop.mediaflow.data.local.MediaVisibility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.util.concurrent.ConcurrentHashMap

/**
 * 控制层：把「视图请求」编排到具体来源后端。
 *
 * 本层只做编排与并发合并，不再关心数据如何投影——投影已下沉到
 * [com.lollipop.mediaflow.data.source.MediaCatalog] / [com.lollipop.mediaflow.data.source.MediaView]。
 *
 * 每次请求传入一个 [SourceState]（如 [com.lollipop.mediaflow.data.local.LocalState]），
 * 控制层据此得到来源标识与可见性，再转发给对应后端。
 */
sealed class SourceLoader {

    /** 从缓存投影（快），不触发扫描。 */
    abstract suspend fun fill(context: Context, state: SourceState)

    /** 完整刷新（扫描 / 拉取）。 */
    abstract suspend fun refresh(context: Context, state: SourceState)

    /** 分页 / 增量加载更多。 */
    abstract suspend fun loadMore(context: Context, state: SourceState)

    /**
     * Local 实现：委托给 [LocalMediaBackend]。
     *
     * 共享粒度从 (visibility, mediaType) 提升为 **visibility**：
     * 视频 / 图片的并发 refresh 在此即可合并为一次（后端内还有一层同类合并）。
     */
    object Local : SourceLoader() {

        private val refreshJobs = ConcurrentHashMap<MediaVisibility, Job>()

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        override suspend fun fill(context: Context, state: SourceState) {
            LocalMediaBackend.fill(context, state.visibility)
        }

        override suspend fun refresh(context: Context, state: SourceState) {
            val existing = refreshJobs[state.visibility]
            if (existing != null && existing.isActive) {
                // 同可见性的并发 refresh 复用同一扫描，随后各自返回。
                existing.join()
                return
            }
            val job: Job = scope.async {
                runCatching {
                    state.setLoading(true)
                    state.setError(null)
                    LocalMediaBackend.refresh(context, state.visibility)
                }.onFailure {
                    state.setError(it)
                }.also {
                    state.setLoading(false)
                }
                Unit
            }
            refreshJobs[state.visibility] = job
            runCatching { job.join() }
            refreshJobs.remove(state.visibility)
        }

        override suspend fun loadMore(context: Context, state: SourceState) {
            LocalMediaBackend.loadMore(context, state.visibility)
        }
    }
}
