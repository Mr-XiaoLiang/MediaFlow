package com.lollipop.auditory.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.lollipop.auditory.state.UIState
import com.lollipop.common.ui.theme.Typography

@Composable
fun MonetColorTheme(
    content: @Composable () -> Unit
) {
    val playingHash by remember { UIState.playingHash }
    val isDarkMode = isSystemInDarkTheme()
    // 获取原始计算的颜色
    val rawScheme = remember(playingHash, isDarkMode) {
        MonetColor.createTheme(playingHash, isDarkMode)
    }

    // 给核心颜色增加动画平滑效果
    val animatedPrimary by animateColorAsState(rawScheme.primary, tween(800))
    val animatedSurface by animateColorAsState(rawScheme.surface, tween(800))
    val animatedOnSurface by animateColorAsState(rawScheme.onSurface, tween(800))

    val animatedColorScheme = rawScheme.copy(
        primary = animatedPrimary,
        onPrimary = animatedOnSurface, // 同步文字色
        surface = animatedSurface,
        onSurface = animatedOnSurface
    )

    MaterialTheme(
        colorScheme = animatedColorScheme,
        typography = Typography,
        content = content
    )
}