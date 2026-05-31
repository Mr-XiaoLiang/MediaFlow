package com.lollipop.mediaflow

import android.app.Application
import com.lollipop.common.tools.LLog
import com.lollipop.common.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.data.ArchiveManager
import com.lollipop.mediaflow.data.MediaStore
import com.lollipop.mediaflow.data.MediaVisibility
import com.lollipop.mediaflow.page.play.PhotoFlowActivity
import com.lollipop.mediaflow.page.play.VideoFlowActivity
import com.lollipop.mediaflow.tools.MediaPlayLauncher
import com.lollipop.mediaflow.tools.Preferences

class LApplication : Application() {

    companion object {
        var launchTime = 0L
    }

    override fun onCreate() {
        super.onCreate()
        LLog.isDebug = BuildConfig.DEBUG
        launchTime = System.currentTimeMillis()
        Preferences.init(this)
        MediaPlayLauncher.bindImpl(
            videoFlow = VideoFlowActivity::class.java,
            photoFlow = PhotoFlowActivity::class.java
        )
        preload()
    }

    private fun preload() {
        MediaStore.loadStore(this, MediaVisibility.Public).fetch(isRefresh = false) {}
        MediaStore.loadStore(this, MediaVisibility.Private).fetch(isRefresh = false) {}
        if (Preferences.isQuickArchiveEnable.get()) {
            ArchiveManager.init(this)
        }
    }

}