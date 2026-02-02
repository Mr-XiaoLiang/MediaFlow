package com.lollipop.mediaflow.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.viewpager2.widget.ViewPager2
import com.lollipop.mediaflow.databinding.ActivityFlowBinding

abstract class BasicFlowActivity : BasicInsetsActivity() {

    protected var currentOrientation: Orientation = Orientation.PORTRAIT

    private val basicBinding by lazy {
        ActivityFlowBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(basicBinding.root)
        initInsetsListener()
        bindDrawerListener()
        basicBinding.drawerPanel.addView(
            createDrawerPanel(),
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        updateBlur()
        checkOrientation(resources.configuration)
    }

    private fun bindDrawerListener() {
        basicBinding.drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
            }

            override fun onDrawerOpened(drawerView: View) {
                onDrawerChanged(true)
            }

            override fun onDrawerClosed(drawerView: View) {
                onDrawerChanged(false)
            }

            override fun onDrawerStateChanged(newState: Int) {
            }
        })
    }

    private fun initInsetsListener() {
        initInsetsListener(basicBinding.root)
        bindGuidelineInsets(
            leftGuideline = basicBinding.startGuideLine,
            topGuideline = basicBinding.topGuideLine,
            rightGuideline = basicBinding.endGuideLine,
            bottomGuideline = basicBinding.bottomGuideLine,
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        checkOrientation(newConfig)
        updateBlur()
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

    protected fun setOrientation(orientation: Orientation) {
        currentOrientation = orientation
        requestedOrientation = if (orientation == Orientation.LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        onOrientationChanged(currentOrientation)
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

    protected fun hideDecorationPanel() {
        basicBinding.decorationPanel.visibility = View.GONE
    }

    protected fun showDecorationPanel() {
        basicBinding.decorationPanel.visibility = View.VISIBLE
    }

    private fun updateBlur() {
        BlueHelper.bind(
            window,
            basicBinding.blurTarget,
            basicBinding.menuBtnBlur,
            basicBinding.backBtnBlur,
        )
    }


    protected abstract fun onOrientationChanged(orientation: Orientation)

    protected fun changeDrawerState(isOpen: Boolean) {
        if (isOpen) {
            basicBinding.drawerLayout.openDrawer(basicBinding.drawerPanel)
        } else {
            basicBinding.drawerLayout.closeDrawer(basicBinding.drawerPanel)
        }
    }

    protected abstract fun onDrawerChanged(isOpen: Boolean)

    protected abstract fun createDrawerPanel(): View

    protected abstract fun buildContentPanel(viewPager2: ViewPager2)

    protected enum class Orientation {
        PORTRAIT,
        LANDSCAPE
    }

}