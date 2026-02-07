package com.lollipop.mediaflow.ui

import com.lollipop.mediaflow.data.MediaType
import com.lollipop.mediaflow.data.MediaVisibility
import com.lollipop.mediaflow.page.main.MainMediaSubPage

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
    ),

}