package com.lollipop.mediaflow.ui;

import com.lollipop.mediaflow.data.MediaSort;
import com.lollipop.mediaflow.data.MediaType;
import com.lollipop.mediaflow.data.MediaVisibility;
import com.lollipop.mediaflow.page.main.MainMediaSubPage;
import com.lollipop.mediaflow.tools.Preferences;

enum class HomePage(
    val key: String,
    val pageClass: Class<out MainMediaSubPage>,
    val mediaType: MediaType
) {


    PublicVideo(
        key = "public_video",
        pageClass = MainMediaSubPage.PublicVideo::class.java,
        mediaType = MediaType.Video
    ),

    PublicPhoto(
        key = "public_photo",
        pageClass = MainMediaSubPage.PublicPhoto::class.java,
        mediaType = MediaType.Image
    );

    val visibility: MediaVisibility
        get() {
            return MediaVisibility.Public
        }

    var sortType: MediaSort
        get() {
            return when (this) {
                PublicVideo -> Preferences.publicVideoSort.get()
                PublicPhoto -> Preferences.publicPhotoSort.get()
            }
        }
        set(value) {
            when (this) {
                PublicVideo -> Preferences.publicVideoSort.set(value)
                PublicPhoto -> Preferences.publicPhotoSort.set(value)
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
            mediaType: MediaType
        ): HomePage {
            return when (mediaType) {
                MediaType.Image -> PublicPhoto
                MediaType.Video -> PublicVideo
            }
        }
    }

}