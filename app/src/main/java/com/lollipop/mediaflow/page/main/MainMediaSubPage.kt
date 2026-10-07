package com.lollipop.mediaflow.page.main

import com.lollipop.mediaflow.ui.HomePage

sealed class MainMediaSubPage(page: HomePage) : BasicMediaGridPage(page) {

    class PublicVideo : MainMediaSubPage(page = HomePage.PublicVideo) {

    }

    class PublicPhoto : MainMediaSubPage(page = HomePage.PublicPhoto) {

    }

    class PrivateVideo : MainMediaSubPage(page = HomePage.PrivateVideo) {

    }

    class PrivatePhoto : MainMediaSubPage(page = HomePage.PrivatePhoto) {

    }

}