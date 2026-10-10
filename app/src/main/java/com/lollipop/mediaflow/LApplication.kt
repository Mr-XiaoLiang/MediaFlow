package com.lollipop.mediaflow

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.lollipop.common.tools.CrashHelper
import com.lollipop.common.tools.LLog
import com.lollipop.common.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.data.local.ArchiveManager
import com.lollipop.mediaflow.data.local.LocalState
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.mediaflow.ui.image.installFlowComponents

class LApplication : Application(), SingletonImageLoader.Factory {

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

    /**
     * Coil 3 的全局 ImageLoader。
     *
     * 通过 [installFlowComponents] 安装视频帧解码器与 OkHttp 网络组件，
     * 保证 [`com.lollipop.mediaflow.ui.image.MediaImage`] 对「本地视频封面 / 远程图片」都可用。
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .installFlowComponents()
            .build()
    }

    private fun preload() {
        ArchiveManager.init(this)
    }

}