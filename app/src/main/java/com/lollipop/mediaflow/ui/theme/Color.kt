package com.lollipop.mediaflow.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

class ThemeColor(
    val windowBackground: Color,
    val buttonBackground: Color,
    val buttonSlider: Color,
    val buttonText: Color,
    val buttonTextPrimary: Color,
    val buttonMask: Color,
    val preferencesGroup: Color,
    /** 未播放进度段的颜色（`progress_inactive`）。深 / 浅色下取值相同，故不随主题变化。 */
    val progressInactive: Color
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
    progressInactive = Color(0x55888888),
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
    progressInactive = Color(0x55888888),
)

/**
 * 选中项强调色：以主题主色为基调，向「该主题下更易辨认的方向」叠加。
 *
 * - 深色主题：主色向白色提亮，避免青蓝色在深色弹层上过暗；
 * - 浅色主题：主色向黑色加深，避免青蓝色在浅色弹层上过浅发虚。
 *
 * 返回值是不透明实色，而不是带 alpha 的颜色：alpha 会再次与背景混合，
 * 反而削弱选中态与普通文本之间的对比度。
 *
 * @param primary 主题主色（保持其青蓝色相）。
 * @param darkTheme 当前是否深色主题，决定叠加方向。
 */
fun selectionAccentColor(primary: Color, darkTheme: Boolean): Color {
    val mixTarget = if (darkTheme) Color.White else Color.Black
    val mixRatio = if (darkTheme) 0.38F else 0.40F
    return lerp(primary, mixTarget, mixRatio)
}
