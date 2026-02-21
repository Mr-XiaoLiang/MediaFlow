package com.lollipop.mediaflow.ui

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.databinding.ActivityFlowBinding

abstract class BasicFlowActivity : CustomOrientationActivity() {

    private val basicBinding by lazy {
        ActivityFlowBinding.inflate(layoutInflater)
    }

    protected var isFullscreen = false
        private set

    protected var isDecorationShown = true
        private set

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
        basicBinding.contentContainer.addView(
            createContentPanel(),
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        basicBinding.backBtn.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        basicBinding.fullscreenBtn.setOnClickListener {
            isFullscreen = !isFullscreen
            updateFullscreen()
        }
        updateBlur()
    }

    protected fun updateFullscreen() {
        if (isFullscreen || currentOrientation == Orientation.LANDSCAPE) {
            basicBinding.fullscreenBtnIcon.setImageResource(R.drawable.fullscreen_exit_24)
            hideSystemUI()
        } else {
            basicBinding.fullscreenBtnIcon.setImageResource(R.drawable.fullscreen_24)
            showSystemUI()
        }
        if (currentOrientation == Orientation.LANDSCAPE) {
            basicBinding.fullscreenBtn.isVisible = false
        } else {
            basicBinding.fullscreenBtn.isVisible = true
        }
    }

    protected fun changeDecoration(isVisibility: Boolean) {
        isDecorationShown = isVisibility
        if (isVisibility) {
            basicBinding.decorationPanel.visibility = View.VISIBLE
        } else {
            basicBinding.decorationPanel.visibility = View.GONE
        }
        if (currentOrientation == Orientation.LANDSCAPE) {
            basicBinding.fullscreenBtn.isVisible = false
        } else {
            basicBinding.fullscreenBtn.isVisible = true
        }
    }

    private fun bindDrawerListener() {
        basicBinding.menuBtn.setOnClickListener {
            changeDrawerState(true)
        }
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

    protected fun updateTitle(charSequence: CharSequence) {
        basicBinding.titleView.text = charSequence
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
        updateBlur()
        updateFullscreen()
    }

    private fun updateBlur() {
        BlurHelper.bind(
            window,
            basicBinding.blurTarget,
            basicBinding.menuBtnBlur,
            basicBinding.backBtnBlur,
            basicBinding.fullscreenBtnBlur,
        )
    }

    protected fun changeDrawerState(isOpen: Boolean) {
        if (isOpen) {
            basicBinding.drawerLayout.openDrawer(basicBinding.drawerPanel)
        } else {
            basicBinding.drawerLayout.closeDrawer(basicBinding.drawerPanel)
        }
    }

    protected abstract fun onDrawerChanged(isOpen: Boolean)

    protected abstract fun createDrawerPanel(): View

    protected abstract fun createContentPanel(): View

}