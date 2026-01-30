package com.lollipop.mediaflow

import android.app.Application
import com.lollipop.mediaflow.data.MediaStore
import com.lollipop.mediaflow.data.MediaVisibility
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog

class LApplication : Application() {

    private val log = registerLog()

    override fun onCreate() {
        super.onCreate()
        preloadMediaStore()
    }

    private fun preloadMediaStore() {
        log.i("preloadMediaStore: 开始预加载媒体库")
        MediaStore.loadStore(this, MediaVisibility.Public).load { }
        MediaStore.loadStore(this, MediaVisibility.Private).load { }
    }

}