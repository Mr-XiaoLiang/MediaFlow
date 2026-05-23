package com.lollipop.mediaflow.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.annotation.CallSuper
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.drawerlayout.widget.DrawerLayout
import com.lollipop.common.ui.page.CustomOrientationActivity
import com.lollipop.common.ui.page.GuidelineInsetsHelper
import com.lollipop.common.ui.page.PageOrientation
import com.lollipop.common.ui.view.BlurHelper
import com.lollipop.common.ui.view.IconPopupMenu
import com.lollipop.common.ui.view.SimpleGestureLayout
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.databinding.ActivityFlowBinding
import com.lollipop.mediaflow.page.flow.FlowSidePanelDelegate
import com.lollipop.mediaflow.tools.Preferences

abstract class BasicFlowActivity : CustomOrientationActivity() {

    private val basicBinding by lazy {
        ActivityFlowBinding.inflate(layoutInflater)
    }

    protected var isFullscreen = false
        private set

    protected var isDecorationShown = true
        private set

    protected var endGuideSize = 0

    protected val sidePanelDelegate by lazy {
        FlowSidePanelDelegate(lifecycle, basicBinding.sidePanelContent, ::onSideItemClick)
    }

    private val backBtnVisibleFilter by lazy {
        PipVisibleFilter(basicBinding.backBtn)
    }

    protected val menuBarVisibleFilter by lazy {
        VisibleFilterGroup.Or(basicBinding.menuBar)
    }

    private val sidePanelBtnVisibleFilter by lazy {
        PipVisibleFilter(basicBinding.sidePanelBtn).also {
            menuBarVisibleFilter.register(it)
        }
    }

    private val fullscreenBtnVisibleFilter by lazy {
        FullscreenBtnVisibleFilter(basicBinding.fullscreenBtn).also {
            menuBarVisibleFilter.register(it)
        }
    }

    private val menuBtnVisibleFilter by lazy {
        PipVisibleFilter(basicBinding.menuBtn).also {
            menuBarVisibleFilter.register(it)
        }
    }

    private val rotateVisibleFilter by lazy {
        PipVisibleFilter(basicBinding.rotateBtn).also {
            menuBarVisibleFilter.register(it)
        }
    }

    private val titleVisibleFilter by lazy {
        PipVisibleFilter(basicBinding.titleView)
    }

    private val tagVisibleFilter by lazy {
        PipVisibleFilter(basicBinding.tagGroup)
    }

    private val contentInsetsHelper = GuidelineInsetsHelper()

    protected var screenRotateMode = ScreenRotate.ROTATE_LOCK

    private val rotatePopupHolder by lazy {
        IconPopupMenu.hold(::buildRotatePopup)
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
        basicBinding.rotateBtn.setOnClickListener {
            rotatePopupHolder.show(it)
        }
        initSidePanelGesture()
        sidePanelDelegate.onCreate()
        basicBinding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        updateBlur()
        updateScreenRotate(
            ScreenRotate.findByTag(Preferences.lastRotateMode.get()) ?: ScreenRotate.ROTATE_LOCK
        )
    }

    override fun onResume() {
        super.onResume()
        backBtnVisibleFilter.preference.setVisible(Preferences.isShowBackBtn.get())
        sidePanelBtnVisibleFilter.preference.setVisible(Preferences.isShowSidePanelBtn.get())
        fullscreenBtnVisibleFilter.preference.setVisible(Preferences.isShowFullscreenBtn.get())
        fullscreenBtnVisibleFilter.update(currentOrientation)
        menuBtnVisibleFilter.preference.setVisible(Preferences.isShowDrawerBtn.get())
        rotateVisibleFilter.preference.setVisible(Preferences.isShowRotateBtn.get())
        titleVisibleFilter.preference.setVisible(Preferences.isShowTitle.get())
        tagVisibleFilter.preference.setVisible(Preferences.isShowTag.get())
        basicBinding.sidePanelGestureView.isVisible = Preferences.isSidePanelGestureEnable.get()
    }

    protected fun changeSidePanel(isShow: Boolean = !basicBinding.sidePanel.isVisible) {
        basicBinding.sidePanel.isVisible = isShow
        basicBinding.endGuideLine.updateLayoutParams<ConstraintLayout.LayoutParams> {
            guideEnd = if (isShow) {
                contentInsetsHelper.minEdge.right
            } else {
                endGuideSize
            }
        }
        basicBinding.sidePanelBtn.setImageResource(
            if (isShow) {
                R.drawable.right_panel_close_24
            } else {
                R.drawable.right_panel_open_24
            }
        )
        onSidePanelUpdate(isShow)
    }

    private fun initSidePanelGesture() {
        basicBinding.sidePanelGestureView.also { view ->
            view.onGesture {
                onSidePanelGesture(it)
            }
            view.registerGesture(
                SimpleGestureLayout.GestureType.LeftToRight,
            )
            view.registerGesture(
                SimpleGestureLayout.GestureType.RightToLeft,
            )
        }
    }

    private fun onSidePanelGesture(type: SimpleGestureLayout.GestureType) {
        when (type) {
            SimpleGestureLayout.GestureType.LeftToRight -> {
                changeSidePanel(false)
            }

            SimpleGestureLayout.GestureType.RightToLeft -> {
                changeSidePanel(true)
            }

            else -> {}
        }
    }

    @CallSuper
    override fun onWindowInsetsChanged(left: Int, top: Int, right: Int, bottom: Int) {
        basicBinding.sidePanel.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            rightMargin = right
        }
        sidePanelDelegate.onInsetsChanged(left, top, right, bottom)
    }

    protected fun isSidePanelShown(): Boolean {
        return basicBinding.sidePanel.isVisible
    }

    protected open fun onSidePanelUpdate(isShown: Boolean) {

    }

    protected abstract fun onSideItemClick(mediaInfo: MediaInfo.File, position: Int)

    protected fun updateSideMediaData(list: List<MediaInfo.File>) {
        sidePanelDelegate.onDataChanged(list)
    }

    protected fun currentSidePosition(): Int {
        return sidePanelDelegate.currentPosition()
    }

    protected fun onSideSelected(mediaInfo: MediaInfo?, position: Int) {
        sidePanelDelegate.onSelected(mediaInfo, position)
    }

    protected fun removeSideAt(position: Int) {
        sidePanelDelegate.removeAt(position)
    }

    protected fun updateFullscreen() {
        if (isFullscreen || currentOrientation == PageOrientation.LANDSCAPE) {
            basicBinding.fullscreenBtn.setImageResource(R.drawable.fullscreen_exit_24)
            hideSystemUI()
        } else {
            basicBinding.fullscreenBtn.setImageResource(R.drawable.fullscreen_24)
            showSystemUI()
        }
        fullscreenBtnVisibleFilter.update(currentOrientation)
    }

    protected fun updateScreenRotate(mode: ScreenRotate) {
        Preferences.lastRotateMode.set(mode.tag)
        basicBinding.rotateBtn.setImageResource(mode.icon)
        screenRotateMode = mode
        setRequestedOrientation(mode.tag)
    }

    protected fun changeDecoration(isVisibility: Boolean) {
        isDecorationShown = isVisibility
        if (isVisibility) {
            basicBinding.decorationPanel.visibility = View.VISIBLE
        } else {
            basicBinding.decorationPanel.visibility = View.GONE
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
            titleVisibleFilter.base.setVisible(titleValue.isNotEmpty())
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
        registerGuidelineInsetsListener(contentInsetsHelper)
        contentInsetsHelper.bindGuidelineInsets(
            leftGuideline = basicBinding.startGuideLine,
            topGuideline = basicBinding.topGuideLine,
            rightGuideline = basicBinding.endGuideLine,
            bottomGuideline = basicBinding.bottomGuideLine,
        )
        contentInsetsHelper.onFilter { insets ->
            endGuideSize = insets.right
            return@onFilter if (isSidePanelShown()) {
                GuidelineInsetsHelper.SimpleEdgeSize(
                    insets.left,
                    insets.top,
                    contentInsetsHelper.minEdge.right,
                    insets.bottom
                )
            } else {
                insets
            }
        }
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

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        backBtnVisibleFilter.onPipChanged(isInPictureInPictureMode)
        titleVisibleFilter.onPipChanged(isInPictureInPictureMode)
        tagVisibleFilter.onPipChanged(isInPictureInPictureMode)
        sidePanelBtnVisibleFilter.onPipChanged(isInPictureInPictureMode)
        fullscreenBtnVisibleFilter.onPipChanged(isInPictureInPictureMode)
        menuBtnVisibleFilter.onPipChanged(isInPictureInPictureMode)
        rotateVisibleFilter.onPipChanged(isInPictureInPictureMode)
    }

    protected abstract fun onDrawerChanged(isOpen: Boolean)

    protected abstract fun createDrawerPanel(): View

    protected abstract fun createContentPanel(): View

    private fun buildRotatePopup(builder: IconPopupMenu.Builder) {
        ScreenRotate.entries.forEach { item ->
            builder.addMenu(
                tag = item.name,
                titleRes = item.label,
                iconRes = item.icon
            )
        }
        builder
            .gravity(Gravity.END)
            .offsetDp(0, 8)
            .onClick { item ->
                val newMode = ScreenRotate.findByName(item.tag) ?: ScreenRotate.ROTATE_LOCK
                updateScreenRotate(newMode)
                true
            }
    }

    protected class FullscreenBtnVisibleFilter(
        targetView: View,
    ) : PipVisibleFilter(targetView) {

        val orientation = pair(true)

        fun update(o: PageOrientation) {
            orientation.setVisible(o == PageOrientation.PORTRAIT)
        }

    }

    protected enum class ScreenRotate(val tag: Int, val icon: Int, val label: Int) {
        ROTATE_LOCK(
            tag = ActivityInfo.SCREEN_ORIENTATION_FULL_USER,
            icon = R.drawable.mobile_rotate_lock_24,
            label = R.string.screen_rotate_lock_user
        ),
        ROTATE_AUTO(
            tag = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,
            icon = R.drawable.mobile_rotate_24,
            label = R.string.screen_rotate_auto
        ),
        PORTRAIT(
            tag = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            icon = R.drawable.mobile_lock_portrait_24,
            label = R.string.screen_rotate_lock_portrait
        ),
        LANDSCAPE(
            tag = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            icon = R.drawable.mobile_lock_landscape_24,
            label = R.string.screen_rotate_lock_landscape
        );

        companion object {
            fun findByTag(tag: Int): ScreenRotate? {
                return ScreenRotate.entries.find { it.tag == tag }
            }

            fun findByName(name: String): ScreenRotate? {
                return ScreenRotate.entries.find { it.name == name }
            }
        }

    }

}