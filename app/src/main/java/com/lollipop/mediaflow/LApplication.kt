package com.lollipop.mediaflow

import android.app.Application
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
    }

}