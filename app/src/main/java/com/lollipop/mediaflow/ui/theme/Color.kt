package com.lollipop.mediaflow.ui.theme

import androidx.compose.ui.graphics.Color

class ThemeColor(
    val buttonBackground: Color,
    val buttonSlider: Color,
    val buttonText: Color,
    val buttonMask: Color,
)

val DarkThemeColor = ThemeColor(
    buttonBackground = Color(0xFF007E75),
    buttonSlider = Color(0xBE76C7BF),
    buttonText = Color(0xFFFFFFFF),
    buttonMask = Color(0x50DCDCDC),
)

val LightThemeColor = ThemeColor(
    buttonBackground = Color(0xFFFFFFFF),
    buttonSlider = Color(0xBE76C7BF),
    buttonText = Color(0xFF333333),
    buttonMask = Color(0x50DCDCDC),
)
