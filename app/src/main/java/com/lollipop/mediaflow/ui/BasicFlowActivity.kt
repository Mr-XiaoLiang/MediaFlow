package com.lollipop.mediaflow.ui

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.annotation.CallSuper
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.Insets
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
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

    protected var endGuideSize = 0

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
        basicBinding.sidePanelBtn.setOnClickListener {
            changeSidePanel()
        }
        basicBinding.fullscreenBtn.setOnClickListener {
            isFullscreen = !isFullscreen
            updateFullscreen()
        }
        updateBlur()
    }

    protected fun changeSidePanel() {
        val newState = !basicBinding.sidePanel.isVisible
        basicBinding.sidePanel.isVisible = newState
        basicBinding.endGuideLine.updateLayoutParams<ConstraintLayout.LayoutParams> {
            guideEnd = if (newState) {
                minEdge
            } else {
                endGuideSize
            }
        }
        basicBinding.sidePanelBtn.setImageResource(
            if (newState) {
                R.drawable.right_panel_close_24
            } else {
                R.drawable.right_panel_open_24
            }
        )
        onSidePanelUpdate(newState)
    }

    override fun filterGuidelineInsets(insets: Insets): Insets {
        endGuideSize = insets.right
        if (isSidePanelShown()) {
            return Insets.of(insets.left, insets.top, minEdge, insets.bottom)
        }
        return super.filterGuidelineInsets(insets)
    }

    @CallSuper
    override fun onWindowInsetsChanged(left: Int, top: Int, right: Int, bottom: Int) {
        basicBinding.sidePanel.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            rightMargin = right
        }
    }

    protected fun isSidePanelShown(): Boolean {
        return basicBinding.sidePanel.isVisible
    }

    protected open fun onSidePanelUpdate(isShown: Boolean) {

    }

    protected fun updateFullscreen() {
        if (isFullscreen || currentOrientation == Orientation.LANDSCAPE) {
            basicBinding.fullscreenBtn.setImageResource(R.drawable.fullscreen_exit_24)
            hideSystemUI()
        } else {
            basicBinding.fullscreenBtn.setImageResource(R.drawable.fullscreen_24)
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

    protected fun updateTitle(
        titleValue: CharSequence,
        size: CharSequence,
        format: CharSequence,
        duration: CharSequence
    ) {
        basicBinding.root.post {
            basicBinding.titleView.text = titleValue
            basicBinding.sizeTagView.text = size
            basicBinding.formatTagView.text = format
            basicBinding.durationTagView.text = duration
            basicBinding.titleView.isVisible = titleValue.isNotEmpty()
            basicBinding.sizeTagView.isVisible = size.isNotEmpty()
            basicBinding.formatTagView.isVisible = format.isNotEmpty()
            basicBinding.durationTagView.isVisible = duration.isNotEmpty()
        }
        log.i("updateTitle: $titleValue, $size, $format, $duration")
        if (titleValue.isEmpty()) {
            log.e("updateTitle: isEmpty", RuntimeException())
        }
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
            basicBinding.menuBarBlur,
            basicBinding.backBtnBlur,
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