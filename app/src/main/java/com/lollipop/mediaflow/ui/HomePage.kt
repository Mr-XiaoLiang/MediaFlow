package com.lollipop.mediaflow.ui

import com.lollipop.mediaflow.page.main.MainMediaSubPage

enum class HomePage(
    val key: String,
    val pageClass: Class<out MainMediaSubPage>
) {

    PublicVideo(key = "public_video", pageClass = MainMediaSubPage.PublicVideo::class.java),
    PublicPhoto(key = "public_photo", pageClass = MainMediaSubPage.PublicPhoto::class.java),
    PrivateVideo(key = "private_video", pageClass = MainMediaSubPage.PrivateVideo::class.java),
    PrivatePhoto(key = "private_photo", pageClass = MainMediaSubPage.PrivatePhoto::class.java),

}