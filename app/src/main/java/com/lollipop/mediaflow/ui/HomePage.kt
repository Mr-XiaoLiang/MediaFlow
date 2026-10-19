package com.lollipop.mediaflow.ui

import com.lollipop.mediaflow.data.common.MediaSort
import com.lollipop.mediaflow.data.MediaSource
import com.lollipop.mediaflow.data.local.MediaType
import com.lollipop.mediaflow.data.local.MediaVisibility
import com.lollipop.mediaflow.data.local.LocalState

enum class HomePage(
    val key: String,
    val visibility: MediaVisibility,
    val mediaType: MediaType
) {

    PublicVideo(
        key = "public_video",
        visibility = MediaVisibility.Public,
        mediaType = MediaType.Video
    ),
    PublicPhoto(
        key = "public_photo",
        visibility = MediaVisibility.Public,
        mediaType = MediaType.Image
    ),
    PrivateVideo(
        key = "private_video",
        visibility = MediaVisibility.Private,
        mediaType = MediaType.Video
    ),
    PrivatePhoto(
        key = "private_photo",
        visibility = MediaVisibility.Private,
        mediaType = MediaType.Image
    );

    var sortType: MediaSort
        get() {
            return localState.sort.value
        }
        set(value) {
            localState.setSort(value)
        }

    /** 本页对应的 Local 业务状态单例（sort / scopeId / loading / error）。 */
    val localState: LocalState
        get() {
            return LocalState.of(visibility, mediaType)
        }

    /** 本页对应的展示来源实例（local / webDAV 列表）。 */
    val source: MediaSource
        get() {
            return MediaSource.of(visibility, mediaType)
        }

    companion object {

        /** 公开（非隐私）页面，锁定状态下展示的 2 个 Tab。 */
        val publicPages: List<HomePage> = listOf(PublicVideo, PublicPhoto)

        /** 隐私页面，解锁后追加展示的 2 个 Tab。 */
        val privatePages: List<HomePage> = listOf(PrivateVideo, PrivatePhoto)

        fun findPage(
            key: String
        ): HomePage? {
            return entries.firstOrNull { it.key == key }
        }

        fun findPage(
            visibility: MediaVisibility,
            mediaType: MediaType
        ): HomePage {
            return when (visibility) {
                MediaVisibility.Public -> {
                    when (mediaType) {
                        MediaType.Image -> PublicPhoto
                        MediaType.Video -> PublicVideo
                    }
                }

                MediaVisibility.Private -> {
                    when (mediaType) {
                        MediaType.Image -> PrivatePhoto
                        MediaType.Video -> PrivateVideo
                    }
                }
            }
        }
    }

}
