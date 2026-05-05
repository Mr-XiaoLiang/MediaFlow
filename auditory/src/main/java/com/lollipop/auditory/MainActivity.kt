package com.lollipop.auditory

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.max
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.lollipop.auditory.databinding.ActivityMainBinding
import com.lollipop.auditory.state.UIState
import com.lollipop.common.ui.page.CustomOrientationActivity
import com.lollipop.common.ui.theme.MediaFlowTheme

class MainActivity : CustomOrientationActivity() {

    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

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
    }

    private fun onPlayerPeekHeightChangedPx(height: Int) {
        UIState.playerPeekHeight.intValue = height
        bottomSheetBehavior.peekHeight = height
    }

    private fun createContentView(): View {
        return ComposeView(this).apply {
            setContent {
                val playerPeekHeight by remember { UIState.playerPeekHeight }
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