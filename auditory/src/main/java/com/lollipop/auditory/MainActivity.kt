package com.lollipop.auditory

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.max
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.lollipop.auditory.audio.AudioControllerDelegate
import com.lollipop.auditory.audio.LocalAudioController
import com.lollipop.auditory.base.AuditoryBasicActivity
import com.lollipop.auditory.data.AudioInfo
import com.lollipop.auditory.databinding.ActivityMainBinding
import com.lollipop.auditory.main.FullScreenPlayerPanel
import com.lollipop.auditory.main.MiniSheetPlayerPanel
import com.lollipop.auditory.main.basic.BasicSheetPanel
import com.lollipop.auditory.model.LocalAudioViewModel
import com.lollipop.auditory.model.ViewModelGroup
import com.lollipop.auditory.page.MainPage
import com.lollipop.auditory.page.PermissionPage
import com.lollipop.auditory.tool.LocalPermissionLauncher
import com.lollipop.auditory.tool.PermissionLauncher
import com.lollipop.auditory.ui.MediaFlowTheme
import com.lollipop.common.ui.page.GuidelineInsetsHelper
import com.lollipop.common.ui.page.PageOrientation
import com.lollipop.common.ui.view.BlurHelper
import kotlinx.coroutines.launch

class MainActivity : AuditoryBasicActivity() {

    override val checkSystemGesturesInsets: Boolean = true
    override val checkSystemBarsInsets: Boolean = true
    override val checkDisplayCutoutInsets: Boolean = true

    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private val fullScreenPlayerPanel by lazy {
        FullScreenPlayerPanel(binding.playerFullPanel)
    }

    private val miniSheetPlayerPanel by lazy {
        MiniSheetPlayerPanel(binding.playerMiniPanel)
    }

    private val permissionLauncher by lazy {
        PermissionLauncher(this, ::onPermissionResult)
    }

    private val bottomSheetBackPressedDispatcher = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    private val audioController by lazy {
        AudioControllerDelegate(this)
    }

    private val sheetPanel by lazy {
        SheetPanelGroup(
            arrayOf(
                miniSheetPlayerPanel,
                fullScreenPlayerPanel
            ),
            bottomSheetBackPressedDispatcher
        )
    }

    private val model = ViewModelGroup(this)

    private val bottomSheetBehavior by lazy {
        BottomSheetBehavior.from(binding.playerSheet)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        initInsetsListener(binding.root)
        binding.contentView.addView(
            createContentView(),
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        log.i("onCreate")
        registerInsets()
        rememberState()
        updateBlur()
        sheetPanel.onCreate()
        permissionLauncher.onCreate()
    }

    private fun onPermissionResult(stateMap: PermissionLauncher.StateMap) {
        log.i("onPermissionResult = ${stateMap.isGranted}")
        if (PermissionLauncher.Permission.ReadMedia.isGranted(stateMap) && model.audio.songs.value.isEmpty()) {
            log.i("onPermissionResult.refresh")
            model.audio.refresh(this)
        }
    }

    private fun registerInsets() {
        registerGuidelineInsetsListener(sheetPanel.guidelineInsetsHelper)
    }

    private fun rememberState() {
        log.i("rememberState")
        // 观察数据变化
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.theme.playingInfo.collect { audioInfo ->
                    onAudioInfoChanged(audioInfo)
                }
            }
        }

        miniSheetPlayerPanel.onSizeChanged { _, height ->
            onPlayerPeekHeightChangedPx(height)
        }
        bottomSheetBehavior.addBottomSheetCallback(sheetPanel.bottomSheetCallback)
        onBackPressedDispatcher.addCallback(bottomSheetBackPressedDispatcher)
    }

    private fun onAudioInfoChanged(audioInfo: AudioInfo?) {
        log.i("onAudioInfoChanged = $audioInfo")
        // TODO()
    }

    private fun onPlayerPeekHeightChangedPx(height: Int) {
        log.i("onPlayerPeekHeightChangedPx = $height")
        model.theme.playerPeekHeight.intValue = height
        bottomSheetBehavior.peekHeight = height
    }

    private fun createContentView(): View {
        log.i("createContentView")
        return ComposeView(this).apply {
            setContent {
                val playerPeekHeight by remember { model.theme.playerPeekHeight }
                MediaFlowTheme {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        val currentDensity = LocalDensity.current
                        val currentDirection = LocalLayoutDirection.current
                        // 确保发生变化的时候，Insets 会被重新计算
                        val finalPadding = remember(
                            innerPadding,
                            playerPeekHeight,
                            currentDensity,
                            currentDirection
                        ) {
                            val playerPeekDp = with(currentDensity) { playerPeekHeight.toDp() }
                            PaddingValues(
                                top = innerPadding.calculateTopPadding(),
                                bottom = max(playerPeekDp, innerPadding.calculateBottomPadding()),
                                start = innerPadding.calculateStartPadding(currentDirection),
                                end = innerPadding.calculateEndPadding(currentDirection)
                            )
                        }
                        CompositionLocalProvider(
                            LocalAudioViewModel provides model.audio,
                            LocalPermissionLauncher provides permissionLauncher.delegate,
                            LocalAudioController provides audioController.controller
                        ) {
                            Content(finalPadding)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Content(innerPadding: PaddingValues) {
        val permissionState by LocalPermissionLauncher.current.isAllGranted
        if (!permissionState) {
            PermissionPage(innerPadding)
            return
        }
        MainPage(innerPadding)
    }

    private fun updateBlur() {
        BlurHelper.bind(
            window,
            binding.blurTarget,
            binding.playerFullPanel.playButtonBlur,
        )
    }

    override fun onStart() {
        super.onStart()
        audioController.onStart()
    }

    override fun onResume() {
        super.onResume()
        sheetPanel.onResume()
        permissionLauncher.onResume()

        try {
            val pfd = contentResolver.openFileDescriptor(
                "content://media/external/audio/media/1000029481".toUri(),
                "r"
            )
            log.i("1000029481: 文件真实存在！文件大小: ${pfd?.statSize}")
            pfd?.close()
        } catch (e: Exception) {
            log.e("1000029481: MediaStore 撒谎了，这首歌在手机里其实根本不存在！", e)
        }
    }

    override fun onPause() {
        super.onPause()
        sheetPanel.onPause()
    }

    override fun onStop() {
        super.onStop()
        audioController.onStop()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateBlur()
    }

    override fun onOrientationChanged(orientation: PageOrientation) {
        super.onOrientationChanged(orientation)
        sheetPanel.onOrientationChanged(orientation)
    }

    override fun onWindowInsetsChanged(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
    }

    private class SheetPanelGroup(
        val panels: Array<BasicSheetPanel>,
        val onBackPressedCallback: OnBackPressedCallback
    ) : BasicSheetPanel() {

        val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(view: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        onBackPressedCallback.isEnabled = true
                        onExpand()
                    }

                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        onBackPressedCallback.isEnabled = false
                        onCollapse()
                    }

                    else -> {}
                }
            }

            override fun onSlide(view: View, offset: Float) {
                onSlide(offset)
            }
        }

        private fun invoke(block: (BasicSheetPanel) -> Unit) {
            panels.forEach(block)
        }

        override fun onCreate() {
            invoke { it.onCreate() }
        }

        override fun onGuidelineInsetsChanged(
            edgeSize: GuidelineInsetsHelper.EdgeSize
        ) {
            invoke { it.guidelineInsetsHelper.dispatch(edgeSize) }
        }

        override fun onSlide(offset: Float) {
            invoke { it.onSlide(offset) }
        }

        override fun onExpand() {
            invoke { it.onExpand() }
        }

        override fun onCollapse() {
            invoke { it.onCollapse() }
        }

        override fun onResume() {
            invoke { it.onResume() }
        }

        override fun onPause() {
            invoke { it.onPause() }
        }

        override fun onOrientationChanged(orientation: PageOrientation) {
            invoke { it.onOrientationChanged(orientation) }
        }

    }

}