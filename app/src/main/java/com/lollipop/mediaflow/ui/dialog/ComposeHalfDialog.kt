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
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.ui.effect.EffectPalette
import com.lollipop.mediaflow.ui.effect.EffectTuning
import com.lollipop.mediaflow.ui.effect.applyEffectBlur
import com.lollipop.mediaflow.ui.effect.readVisualEffectParams
import com.lollipop.mediaflow.ui.effect.rememberVisualEffectParams
import com.lollipop.mediaflow.ui.theme.MediaFlowTheme
import com.lollipop.mediaflow.ui.theme.currentThemeColor

abstract class ComposeHalfDialog : DialogFragment() {

    protected val log = registerLog()

    override fun onCreate(savedInstanceState: Bundle?) {
        // 使用透明窗口主题：系统背景模糊（Android 12+）要求窗口为 translucent
        setStyle(STYLE_NO_TITLE, R.style.Theme_MediaFlow_Dialog)
        super.onCreate(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        val window = dialog?.window ?: return
        updateWindowSize(resources.configuration)
        window.setBackgroundDrawable(Color.Transparent.toArgb().toDrawable())
        val windowParams = window.attributes
        windowParams.dimAmount = 0.5f // 保持背景变暗，但去掉白色框
        window.attributes = windowParams
        // 本弹层是独立 Window，Haze 无法跨 Window 采样，改用系统背景模糊（Android 12+）
        window.applyEffectBlur(EffectTuning.systemBackgroundBlurRadius(readVisualEffectParams()))
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
                        // 特效模式下半透明，让系统背景模糊透出；NONE 时保持不透明纯色
                        val effectParams = rememberVisualEffectParams()
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .clip(MaterialTheme.shapes.large)
                                .background(
                                    color = EffectPalette.dialogContainerColor(
                                        mode = effectParams.mode,
                                        base = currentThemeColor().windowBackground
                                    )
                                ),
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