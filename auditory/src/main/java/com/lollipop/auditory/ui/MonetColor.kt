package com.lollipop.auditory.ui

import android.graphics.Color
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import kotlin.math.abs

object MonetColor {

    fun generateMonetColors(hash: Int, isDarkMode: Boolean): MonetPair {
        // 基础色生成
        val h1 = (abs(hash) * 0.618034f % 1.0f) * 360f
        val s1 = if (isDarkMode) {
            0.35f
        } else {
            0.25f
        }
        // 夜间稍微浓郁一点
        val v1 = if (isDarkMode) {
            0.15f
        } else {
            0.90f
        }
        val colorA = Color.HSVToColor(floatArrayOf(h1, s1, v1))

        // 偏移色（莫奈光影色）
        val direction = if (hash % 2 == 0) 1f else -1f
        val h2 = (h1 + (direction * 30f) + 360f) % 360f

        // 动态 V 处理：避免触底
        val v2 = if (isDarkMode) {
            v1 + 0.1f // 夜间模式：背景微亮，增加层次
        } else {
            v1 - 0.15f // 白天模式：向深处压一点
        }

        // 动态 S 处理：保持色彩生命力
        val s2 = (s1 + 0.1f).coerceAtMost(0.45f)

        val colorB = Color.HSVToColor(floatArrayOf(h2, s2, v2))

        return MonetPair(primary = colorA, secondary = colorB)
    }

    fun androidx.compose.ui.graphics.Color.getOnColor(): androidx.compose.ui.graphics.Color {
        // 计算亮度 (Luminance)
        val luminance = (0.299 * this.red) + (0.587 * this.green) + (0.114 * this.blue)
        return if (luminance > 0.5) {
            androidx.compose.ui.graphics.Color(0xFF2D2D2D) // 浅色背景用深灰（纸张感）
        } else {
            androidx.compose.ui.graphics.Color(0xFFF5F5F0) // 深色背景用浅米色（防刺眼）
        }
    }

    fun createTheme(hash: Int, isDarkMode: Boolean): ColorScheme {
        val color = generateMonetColors(hash, isDarkMode)
        val bgColor = color.primaryColor
        val onColor = bgColor.getOnColor()
        return if (isDarkMode) {
            val textColor = onColor.copy(alpha = 0.85f)
            darkColorScheme(
                primary = bgColor,       // 这里的强调色也可以微调
                onPrimary = textColor, // 确保播放图标清晰
                surface = bgColor,       // 杂志页面背景
                onSurface = textColor,         // 全局文字颜色
                background = androidx.compose.ui.graphics.Color.Black,      // 整体背景
                secondary = bgColor.copy(alpha = 0.5f), // 作为次要强调色
                surfaceVariant = bgColor.copy(alpha = 0.1f), // 作为容器背景
            )
        } else {
            val textColor = onColor.copy(alpha = 0.9f)
            lightColorScheme(
                primary = bgColor,       // 这里的强调色也可以微调
                onPrimary = textColor, // 确保播放图标清晰
                surface = bgColor,       // 杂志页面背景
                onSurface = textColor,         // 全局文字颜色
                background = androidx.compose.ui.graphics.Color.White,      // 整体背景
                secondary = bgColor.copy(alpha = 0.5f), // 作为次要强调色
                surfaceVariant = bgColor.copy(alpha = 0.1f), // 作为容器背景
            )
        }
    }

    class MonetPair(
        val primary: Int,
        val secondary: Int
    ) {

        val primaryColor = androidx.compose.ui.graphics.Color(primary)
        val secondaryColor = androidx.compose.ui.graphics.Color(secondary)

    }

}