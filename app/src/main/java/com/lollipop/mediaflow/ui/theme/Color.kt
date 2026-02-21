package com.lollipop.mediaflow.ui.theme

import androidx.compose.ui.graphics.Color

class ThemeColor(
    val windowBackground: Color,
    val buttonBackground: Color,
    val buttonSlider: Color,
    val buttonText: Color,
    val buttonMask: Color,
    val preferencesGroup: Color
)

val DarkThemeColor = ThemeColor(
    windowBackground = Color(0xFF000000),
    buttonBackground = Color(0xFF007E75),
    buttonSlider = Color(0xBE76C7BF),
    buttonText = Color(0xFFFFFFFF),
    buttonMask = Color(0x50DCDCDC),
    preferencesGroup = Color(0x50464646),
)

val LightThemeColor = ThemeColor(
    windowBackground = Color(0xFFFFFFFF),
    buttonBackground = Color(0xFFFFFFFF),
    buttonSlider = Color(0xBE76C7BF),
    buttonText = Color(0xFF333333),
    buttonMask = Color(0x50DCDCDC),
    preferencesGroup = Color(0x50D7CCCC),
)
