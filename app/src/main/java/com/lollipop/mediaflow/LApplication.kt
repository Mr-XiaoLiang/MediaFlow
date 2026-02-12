package com.lollipop.mediaflow

import android.app.Application
import com.lollipop.mediaflow.data.MediaStore
import com.lollipop.mediaflow.data.MediaVisibility
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.PrivacyLock

class LApplication : Application() {

    companion object {
        var launchTime = 0L
    }

    private val log = registerLog()

    override fun onCreate() {
        super.onCreate()
        PrivacyLock.loadKey(this)
        launchTime = System.currentTimeMillis()
        preload()
    }

    private fun preload() {
        MediaStore.loadStore(this, MediaVisibility.Public).fetch(isRefresh = false) {}
        MediaStore.loadStore(this, MediaVisibility.Private).fetch(isRefresh = false) {}
    }

}