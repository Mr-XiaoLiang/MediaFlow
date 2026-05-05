package com.lollipop.auditory.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.lollipop.auditory.state.UIStateRepository


val DefaultDarkColorScheme = darkColorScheme(
    primary = Color(0xFF007E75),
    secondary = Color(0xFFCC6B6B),
    tertiary = Color(0xFF72935A)
)

@Composable
fun MediaFlowTheme(
    content: @Composable () -> Unit
) {
    val rawScheme by UIStateRepository.playingTheme.collectAsState()

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
        content = content
    )
}
