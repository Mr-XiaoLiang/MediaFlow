package com.lollipop.auditory.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.lollipop.auditory.R
import com.lollipop.auditory.state.UIStateRepository


val DefaultDarkColorScheme = darkColorScheme(
    primary = Color(0xFF007E75),
    secondary = Color(0xFFCC6B6B),
    tertiary = Color(0xFF72935A)
)

@OptIn(ExperimentalTextApi::class)
val NotoVariableFontFamily = FontFamily(
    // 1. 常规体 (W400)：映射到轴数值 400
    Font(
        resId = R.font.noto_serif_sc_variable_font_wght, // 你的单文件 TTF
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),

    // 2. 中粗体 (W500)：映射到轴数值 500
    Font(
        resId = R.font.noto_serif_sc_variable_font_wght, // 依然使用同一个单文件
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),

    // 3. 粗体 (W700)：映射到轴数值 700
    Font(
        resId = R.font.noto_serif_sc_variable_font_wght, // 依然使用同一个单文件
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    )
)

val AppTypography = androidx.compose.material3.Typography(
    // 标题类：自动匹配到 noto_bold.ttf
    titleLarge = TextStyle(
        fontFamily = NotoVariableFontFamily,
        fontWeight = FontWeight.Bold
    ),
    // 核心操作类：自动匹配到 noto_medium.ttf
    labelLarge = TextStyle(
        fontFamily = NotoVariableFontFamily,
        fontWeight = FontWeight.Medium
    ),
    // 普通正文类：自动匹配到 noto_regular.ttf
    bodyLarge = TextStyle(
        fontFamily = NotoVariableFontFamily,
        fontWeight = FontWeight.Normal
    )
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
        content = content,
        typography = AppTypography,
    )
}
