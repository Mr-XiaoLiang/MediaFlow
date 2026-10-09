package com.lollipop.mediaflow.data.source

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.lollipop.mediaflow.data.LMedia
import com.lollipop.mediaflow.data.local.MediaDirectoryTree
import com.lollipop.mediaflow.data.local.MediaVisibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 共享数据单元（写模型 / 唯一真实来源）。
 *
 * 分区键 = **(sourceId, visibility)**。单写者模型：
 * 只有对应的 [MediaBackend] 通过 [publish] 写入，视图（[MediaView]）只读 [snapshot]。
 *
 * ## 线程约定
 * - [publish] 内部**固定切到 [Dispatchers.Main]** 再写 Compose State；
 * - 其耗时前置工作（拍平 / 过滤）由调用方在 [Dispatchers.IO] 上完成后再传入。
 */
class MediaCatalog(
    val sourceId: SourceId,
    val visibility: MediaVisibility
) {

    private val state = mutableStateOf(MediaSnapshot.Unloaded)

    /** 不可变原始快照，供所有 [MediaView] 派生。 */
    val snapshot: State<MediaSnapshot> get() = state

    /**
     * 单写入口。仅在版本真正变化时替换引用，避免无谓的视图重算
     * （尤其是 Random 排序需要"版本不变则结果稳定"）。
     *
     * 约定：**必须切到 Main 写入**，因此本方法为 suspend，并内部 [withContext]。
     */
    internal suspend fun publish(
        items: List<LMedia>,
        trees: List<MediaDirectoryTree>,
        version: Long
    ) {
        withContext(Dispatchers.Main.immediate) {
            // 加载过但没有数据。
            if (items.isEmpty() && trees.isEmpty()) {
                state.value = MediaSnapshot.Empty
                return@withContext
            }
            if (state.value.version == version) {
                return@withContext
            }
            state.value = MediaSnapshot(items = items, trees = trees, version = version)
        }
    }
}
