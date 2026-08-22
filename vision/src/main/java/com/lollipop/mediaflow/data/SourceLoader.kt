package com.lollipop.mediaflow.data

import android.content.Context
import com.lollipop.mediaflow.data.local.MediaStore

/**
 * 控制层：负责实际数据加载与缓存。
 *
 * 密封类，内部只控制有限实现（Local / WebDAV 等），所有实现一眼可见。
 * 具体包装业务（如何读缓存、如何投影）交由各来源实现，这里只做编排。
 *
 * 每次请求都传入一个 [SourceState]（如 [LocalState.PublicVideo]），
 * 控制层通过它反解要刷新的范围与要填充的列表，并同步加载 / 错误状态。
 */
sealed class SourceLoader {

    /**
     * 把当前已缓存的数据投影进展示列表，不做任何加载（快）。
     * 按 [SourceState.scopeId] 决定「全部」还是「只某范围」（choose）。
     */
    abstract fun fill(context: Context, state: SourceState)

    /**
     * 触发一次完整刷新：重扫整 visibility 缓存（较慢），筛选后投影进
     * 对应展示列表，并同步 [SourceState] 的加载 / 错误状态。
     */
    abstract fun refresh(context: Context, state: SourceState)

    /**
     * 触发分页 / 增量加载更多。
     * Local 不做分页，所以 Local 的实现为空。（WebDAV 等有懒加载分层的来源才需要实现。）
     */
    abstract fun loadMore(context: Context, state: SourceState)

    /**
     * Local 实现：就近写在密封类内。
     * 读取缓存（MediaStore.Gallery）→ 按 scopeId / sort 筛选 → 投影进 MediaSource.local。
     */
    object Local : SourceLoader() {

        /** 把 scopeId 同步为 Gallery 的文件夹范围，决定 choose / all。 */
        private fun applyScope(gallery: MediaStore.Gallery, scopeId: String) {
            gallery.setRootDirectory(scopeId)
        }

        /** 从 Gallery 投影结果填充进对应 MediaSource 列表。 */
        private fun fillInto(source: MediaSource, gallery: MediaStore.Gallery) {
            source.local.apply {
                clear()
                addAll(gallery.fileList)
            }
        }

        override fun fill(context: Context, state: SourceState) {
            val gallery = MediaStore.loadGallery(context, state.visibility, state.mediaType)
            applyScope(gallery, state.scopeId.value)
            gallery.loadChoose(state.sort.value) { g, _ ->
                fillInto(MediaSource.of(state.visibility, state.mediaType), g)
            }
        }

        override fun refresh(context: Context, state: SourceState) {
            state.setLoading(true)
            state.setError(null)
            val gallery = MediaStore.loadGallery(context, state.visibility, state.mediaType)
            applyScope(gallery, state.scopeId.value)
            gallery.refresh(state.sort.value) { g, success ->
                // 无论成败，刷新都结束。
                state.setLoading(false)
                if (success) {
                    fillInto(MediaSource.of(state.visibility, state.mediaType), g)
                } else {
                    state.setError(
                        RuntimeException("refresh failed: ${state.visibility}/${state.mediaType}")
                    )
                }
            }
        }

        override fun loadMore(context: Context, state: SourceState) {
            // Local 无分页，空实现。
        }
    }
}
