package com.lollipop.auditory.ui

import android.graphics.Color
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.palette.graphics.Palette

object MonetColor {

    // 0x45d9f3b 是一个常用的无符号 32 位质数，常用于哈希打乱
    private fun scramble(n: Int): Int {
        var x = n
        x = ((x shr 16) xor x) * 0x45d9f3b
        x = ((x shr 16) xor x) * 0x45d9f3b
        x = (x shr 16) xor x
        return x and 0x7FFFFFFF
    }

    private fun getPaletteHue(palette: Palette): Float {
        // 1. 提取核心色相 (H)
        // 优先级：活跃色 > 柔和色 > 默认灰
        val targetColor = palette.getVibrantColor(
            palette.getMutedColor(
                palette.getDominantColor(
                    Color.GRAY
                )
            )
        )

        val hsv = FloatArray(3)
        Color.colorToHSV(targetColor, hsv)

        return hsv[0] // 提取封面最本质的色相
    }

    fun generateMonetColors(palette: Palette?, hash: Int, isDarkMode: Boolean): MonetPair {
        // 1. 先对原始 Hash 进行一次“搅匀” (类似于一种简易的随机数种子生成)
        val finalHash = scramble(hash)

        // 2. 基础色 (ColorA) 计算
        // H: 0 ~ 359
        val h1 = if (palette != null) {
            // 如果有取色器，就直接取主题色
            getPaletteHue(palette)
        } else {
            (finalHash % 360).toFloat()
        }

        // S/V 基础区间定义
        val sBase = if (isDarkMode) {
            0.30f
        } else {
            0.20f
        }
        val vBase = if (isDarkMode) {
            0.15f
        } else {
            0.85f
        }

        // 利用质数切割逻辑获取扰动量 (Delta)
        // S 使用除以 360 后的位域，取模 31（质数，增加随机性）
        val s1 = sBase + ((finalHash / 360) % 31) / 100f
        // V 使用进一步切割后的位域，取模 16，因为取21会在浅色模式下溢出
        val v1 = vBase + ((finalHash / 11160) % 16) / 100f

        val colorA = Color.HSVToColor(floatArrayOf(h1, s1, v1))

        val secondary = generateSecondary(finalHash, isDarkMode, h1, s1, v1)

        return MonetPair(primary = colorA, secondary = secondary)
    }

    private fun generateSecondary(
        hash: Int,
        isDarkMode: Boolean,
        h1: Float,
        s1: Float,
        v1: Float
    ): Int {
        // 3. 偏移色 (ColorB) 计算 —— 莫奈光影逻辑
        // 采样空间：360(H) * 31(S) * 16(V) = 178560
        val direction = if (((hash / 178560) and 1) == 0) {
            1f
        } else {
            -1f
        }
        // 色相偏移：在基础 H1 上旋转 (25-35) 度
        val offsetAngle = 25f + ((hash / 357120) % 11) // 25-35度波动
        val h2 = (h1 + (direction * offsetAngle) + 360f) % 360f

        // 动态 V 偏移：保持对比度
        val v2 = if (isDarkMode) {
            (v1 + 0.10f).coerceAtMost(0.35f) // 暗色模式：微亮
        } else {
            (v1 - 0.15f).coerceAtLeast(0.70f) // 亮色模式：稍深
        }

        // 动态 S 偏移：增加一点生动度
        val s2 = (s1 + 0.10f).coerceAtMost(if (isDarkMode) 0.50f else 0.40f)

        return Color.HSVToColor(floatArrayOf(h2, s2, v2))
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

    fun createTheme(palette: Palette?, hash: Int, isDarkMode: Boolean): ColorScheme {
        val color = generateMonetColors(palette = palette, hash = hash, isDarkMode = isDarkMode)
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