package com.lollipop.mediaflow

import android.app.Application
import com.lollipop.common.tools.CrashHelper
import com.lollipop.common.tools.LLog
import com.lollipop.common.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.data.local.ArchiveManager
import com.lollipop.mediaflow.data.local.LocalState
import com.lollipop.mediaflow.tools.Preferences

class LApplication : Application() {

    companion object {
        var launchTime = 0L
    }

    private val log = registerLog()

    override fun onCreate() {
        super.onCreate()
        CrashHelper.register(this)
        LLog.isDebug = BuildConfig.DEBUG
        launchTime = System.currentTimeMillis()
        Preferences.init(this)
        // 类的加载早于生命周期，State 不适懒加载，这里统一显式初始化各来源的业务状态
        // （从 Preferences 注入持久化的 scopeId 等），确保重启 APP 不丢失状态。
        LocalState.initAll()
        preload()
    }

    private fun preload() {
        ArchiveManager.init(this)
    }

}