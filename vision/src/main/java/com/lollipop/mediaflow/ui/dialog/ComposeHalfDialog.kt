package com.lollipop.mediaflow.ui.dialog

import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import com.lollipop.common.tools.LLog.Companion.registerLog
import com.lollipop.common.ui.theme.MediaFlowTheme
import com.lollipop.common.ui.theme.currentThemeColor

abstract class ComposeHalfDialog : DialogFragment() {

    protected val log = registerLog()

    override fun onStart() {
        super.onStart()
        val window = dialog?.window ?: return
        updateWindowSize(resources.configuration)
        window.setBackgroundDrawable(Color.Transparent.toArgb().toDrawable())
        val windowParams = window.attributes
        windowParams.dimAmount = 0.5f // 保持背景变暗，但去掉白色框
        window.attributes = windowParams
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateWindowSize(newConfig)
    }

    private fun updateWindowSize(config: Configuration) {
        val window = dialog?.window ?: return
        val dm = resources.displayMetrics

        val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isLandscape) {
            // 横屏：宽度占一半，高度占满（或根据需要调整）
            window.setLayout((dm.widthPixels * 0.5).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
            window.setGravity(Gravity.END)
        } else {
            // 竖屏：高度占一半，宽度占满
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (dm.heightPixels * 0.5).toInt())
            window.setGravity(Gravity.TOP)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(inflater.context).apply {
            setContent {
                MediaFlowTheme {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Transparent
                    ) { innerPadding ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .clip(MaterialTheme.shapes.large)
                                .background(color = currentThemeColor().windowBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            DialogContent()
                        }
                    }
                }
            }
        }
    }

    @Composable
    protected abstract fun DialogContent()

}