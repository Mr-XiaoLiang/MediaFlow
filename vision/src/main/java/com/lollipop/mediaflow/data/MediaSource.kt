package com.lollipop.mediaflow.data

import androidx.compose.runtime.snapshots.SnapshotStateList
import com.lollipop.mediaflow.data.local.MediaType
import com.lollipop.mediaflow.data.local.MediaVisibility

/**
 * 面向 UI 的展示来源：每一种展示模式下都有各自的 local / webdav 等来源列表，
 * UI（未来 Compose）只读取对应的 [SnapshotStateList]。
 *
 * 注意：MediaSource 只持有「展示筛选列表」，实际的数据加载与缓存由 [SourceLoader]
 * 控制层负责，二者不同层，不要混在一起。
 */
sealed class MediaSource {

    val local = SnapshotStateList<LMedia>()
    val webDAV = SnapshotStateList<LMedia>()

    object PublicVideo : MediaSource()
    object PrivateVideo : MediaSource()
    object PublicImage : MediaSource()
    object PrivateImage : MediaSource()

    companion object {
        /** 按 (visibility, mediaType) 找到对应的展示列表实例。 */
        fun of(visibility: MediaVisibility, mediaType: MediaType): MediaSource {
            return when (visibility) {
                MediaVisibility.Public -> when (mediaType) {
                    MediaType.Image -> PublicImage
                    MediaType.Video -> PublicVideo
                }
                MediaVisibility.Private -> when (mediaType) {
                    MediaType.Image -> PrivateImage
                    MediaType.Video -> PrivateVideo
                }
            }
        }
    }
}
