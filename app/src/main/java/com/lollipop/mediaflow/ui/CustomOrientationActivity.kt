package com.lollipop.mediaflow.ui

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

abstract class CustomOrientationActivity : BasicInsetsActivity() {

    protected var currentOrientation: Orientation = Orientation.PORTRAIT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkOrientation(resources.configuration)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        checkOrientation(newConfig)
    }

    private fun checkOrientation(configuration: Configuration) {
        val oldOrientation = currentOrientation
        currentOrientation = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Orientation.LANDSCAPE
        } else {
            Orientation.PORTRAIT
        }
        if (oldOrientation != currentOrientation) {
            onOrientationChanged(currentOrientation)
        }
    }

    protected fun hideSystemUI() {
        // 隐藏状态栏和导航栏（真正的全屏）
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    protected fun showSystemUI() {
        // 显示状态栏和导航栏
        WindowCompat.getInsetsController(window, window.decorView).apply {
            show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }
    }

    protected open fun onOrientationChanged(orientation: Orientation) {
        when (orientation) {
            Orientation.PORTRAIT -> {
                showSystemUI()
            }

            Orientation.LANDSCAPE -> {
                hideSystemUI()
            }
        }
    }

    protected enum class Orientation {
        PORTRAIT,
        LANDSCAPE
    }

}