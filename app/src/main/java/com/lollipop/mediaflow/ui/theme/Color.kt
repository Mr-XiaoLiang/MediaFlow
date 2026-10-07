package com.lollipop.mediaflow.ui.theme

import androidx.compose.ui.graphics.Color

class ThemeColor(
    val windowBackground: Color,
    val buttonBackground: Color,
    val buttonSlider: Color,
    val buttonText: Color,
    val buttonTextPrimary: Color,
    val buttonMask: Color,
    val preferencesGroup: Color
)

/**
 * 深色配色：逐项对齐 `values-night/colors.xml`。
 *
 * 注意：这里不是 Material 的 primary/secondary，改动前请先核对 XML 资源。
 */
val DarkThemeColor = ThemeColor(
    windowBackground = Color(0xFF000000),
    buttonBackground = Color(0xFF2C2C2C),
    buttonSlider = Color(0xBE00453D),
    buttonText = Color(0xFFCACACA),
    buttonTextPrimary = Color(0xFF007E75),
    buttonMask = Color(0x30000000),
    preferencesGroup = Color(0xFF282828),
)

/** 浅色配色：逐项对齐 `values/colors.xml`。 */
val LightThemeColor = ThemeColor(
    windowBackground = Color(0xFFFFFFFF),
    buttonBackground = Color(0xFFFFFFFF),
    buttonSlider = Color(0xBE76C7BF),
    buttonText = Color(0xFF333333),
    buttonTextPrimary = Color(0xBE76C7BF),
    buttonMask = Color(0x30DCDCDC),
    preferencesGroup = Color(0x80E5E5E5),
)
