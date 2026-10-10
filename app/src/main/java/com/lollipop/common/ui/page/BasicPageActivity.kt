package com.lollipop.common.ui.page

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lollipop.common.tools.LLog.Companion.registerLog

/**
 * 页面基类（**公共层**）：只放与「insets 采集方式」无关的能力。
 *
 * ## 继承树（View / Compose 两套 insets 实现在本层之下分叉，互不干扰）
 *
 * ```
 * AppCompatActivity
 *  └─ BasicPageActivity                 (本类：日志 / edge-to-edge / 系统栏显隐 / 朝向回调)
 *      ├─ ViewInsetsActivity            (common：View 侧 insets 采集 + Guideline 分发)
 *      │    └─ CustomOrientationActivity
 *      │         └─ BasicFlowActivity   (app：旧 View 外壳)
 *      └─ BasicComposeActivity          (app：Compose 宿主，insets 走 Compose 自洽体系)
 *           └─ BasicFlowComposeActivity (app：Flow 页 Compose 外壳)
 * ```
 *
 * ## 关于朝向
 * 本层只做「检测 + 通知」（[checkOrientation] → [onOrientationChanged]），
 * 默认**不**切换系统栏显隐；系统栏策略由各分支按自身语义决定
 * （View 分支沿用「竖屏显示 / 横屏隐藏」）。
 */
abstract class BasicPageActivity : AppCompatActivity() {

    protected val log by lazy {
        registerLog()
    }

    /** 当前屏幕朝向（由配置变化驱动）。 */
    protected var currentOrientation: PageOrientation = PageOrientation.PORTRAIT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkOrientation(resources.configuration)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        checkOrientation(newConfig)
    }

    /** 状态栏内容是否为深色（浅色背景时为 true）。 */
    protected fun setAppearanceLightStatusBars(isLight: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).also {
            it.isAppearanceLightStatusBars = isLight
        }
    }

    /** 隐藏状态栏与导航栏（真正的全屏）。 */
    protected fun hideSystemUI() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /** 显示状态栏与导航栏。 */
    protected fun showSystemUI() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }
    }

    /** 朝向变化回调；默认不改变系统栏显隐，交由分支实现。 */
    protected open fun onOrientationChanged(orientation: PageOrientation) {
    }

    private fun checkOrientation(configuration: Configuration) {
        val oldOrientation = currentOrientation
        currentOrientation = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            PageOrientation.LANDSCAPE
        } else {
            PageOrientation.PORTRAIT
        }
        if (oldOrientation != currentOrientation) {
            onOrientationChanged(currentOrientation)
        }
    }

}
