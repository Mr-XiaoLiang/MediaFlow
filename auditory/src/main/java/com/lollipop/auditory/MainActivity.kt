package com.lollipop.auditory

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.max
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.lollipop.auditory.base.AuditoryBasicActivity
import com.lollipop.auditory.data.AudioInfo
import com.lollipop.auditory.databinding.ActivityMainBinding
import com.lollipop.auditory.model.ThemeViewModel
import com.lollipop.auditory.ui.MediaFlowTheme
import kotlinx.coroutines.launch

class MainActivity : AuditoryBasicActivity() {

    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private val viewModel: ThemeViewModel by viewModels()

    private val bottomSheetBehavior by lazy {
        BottomSheetBehavior.from(binding.playerSheet)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.contentView.addView(
            createContentView(),
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        rememberState()
    }

    private fun rememberState() {
        // 观察数据变化
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.playingInfo.collect { audioInfo ->
                    onAudioInfoChanged(audioInfo)
                }
            }
        }
    }

    private fun onAudioInfoChanged(audioInfo: AudioInfo?) {
        TODO()
    }

    private fun onPlayerPeekHeightChangedPx(height: Int) {
        viewModel.playerPeekHeight = height
        bottomSheetBehavior.peekHeight = height
    }

    private fun createContentView(): View {
        return ComposeView(this).apply {
            setContent {
                val playerPeekHeight = remember { viewModel.playerPeekHeight }
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
                        Content(finalPadding)
                    }
                }
            }
        }
    }

    @Composable
    private fun Content(innerPadding: PaddingValues) {
        // TODO("这里是 Compose 内容")
    }


    override fun onWindowInsetsChanged(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        // TODO("Not yet implemented")
    }

}