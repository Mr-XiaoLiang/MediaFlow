package com.lollipop.mediaflow.ui

import com.lollipop.mediaflow.data.MediaSort
import com.lollipop.mediaflow.data.MediaType
import com.lollipop.mediaflow.data.MediaVisibility
import com.lollipop.mediaflow.page.main.MainMediaSubPage
import com.lollipop.mediaflow.tools.Preferences

enum class HomePage(
    val key: String,
    val pageClass: Class<out MainMediaSubPage>,
    val visibility: MediaVisibility,
    val mediaType: MediaType
) {

    PublicVideo(
        key = "public_video",
        pageClass = MainMediaSubPage.PublicVideo::class.java,
        visibility = MediaVisibility.Public,
        mediaType = MediaType.Video
    ),
    PublicPhoto(
        key = "public_photo",
        pageClass = MainMediaSubPage.PublicPhoto::class.java,
        visibility = MediaVisibility.Public,
        mediaType = MediaType.Image
    ),
    PrivateVideo(
        key = "private_video",
        pageClass = MainMediaSubPage.PrivateVideo::class.java,
        visibility = MediaVisibility.Private,
        mediaType = MediaType.Video
    ),
    PrivatePhoto(
        key = "private_photo",
        pageClass = MainMediaSubPage.PrivatePhoto::class.java,
        visibility = MediaVisibility.Private,
        mediaType = MediaType.Image
    );

    var sortType: MediaSort
        get() {
            return when (this) {
                PublicVideo -> Preferences.publicVideoSort.get()
                PublicPhoto -> Preferences.publicPhotoSort.get()
                PrivateVideo -> Preferences.privateVideoSort.get()
                PrivatePhoto -> Preferences.privatePhotoSort.get()
            }
        }
        set(value) {
            when (this) {
                PublicVideo -> Preferences.publicVideoSort.set(value)
                PublicPhoto -> Preferences.publicPhotoSort.set(value)
                PrivateVideo -> Preferences.privateVideoSort.set(value)
                PrivatePhoto -> Preferences.privatePhotoSort.set(value)
            }
        }

    companion object {

        fun findPage(
            key: String
        ): HomePage? {
            for (page in entries) {
                if (page.key == key) {
                    return page
                }
            }
            return null
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