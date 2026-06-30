package com.lollipop.mediaflow

import android.app.Application
import com.lollipop.common.tools.LLog
import com.lollipop.mediaflow.data.ArchiveManager
import com.lollipop.mediaflow.data.MediaStore
import com.lollipop.mediaflow.data.MediaVisibility
import com.lollipop.common.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.mediaflow.tools.PrivacyLock

class LApplication : Application() {

    companion object {
        var launchTime = 0L
    }

    private val log = registerLog()

    override fun onCreate() {
        super.onCreate()
        LLog.isDebug = BuildConfig.DEBUG
        PrivacyLock.loadKey(this)
        launchTime = System.currentTimeMillis()
        Preferences.init(this)
        preload()
    }

    private fun preload() {
        if (Preferences.isQuickArchiveEnable.get()) {
            ArchiveManager.init(this)
        }
    }

}