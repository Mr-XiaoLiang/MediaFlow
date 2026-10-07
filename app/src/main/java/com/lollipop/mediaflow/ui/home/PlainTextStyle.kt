package com.lollipop.mediaflow.ui.home

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 复刻 TextView 的默认文字度量。
 *
 * Material3 的 [androidx.compose.material3.MaterialTheme] 会把 `LocalTextStyle` 设为
 * BodyLarge（16sp / 行高 24sp / 字距 0.5sp），而 `Text(fontSize = X)` 只覆盖字号，
 * 行高与字距仍沿用 BodyLarge，于是同一个控件会比原 View 版明显偏高偏宽
 * （例如卡片右下角的时间标签会从「小胶囊」变成「大方块」）。
 *
 * 需要和 View 版尺寸一致的文字请使用它，不要只传 fontSize。
 */
fun plainTextStyle(
    fontSize: TextUnit,
    lineHeightRatio: Float = 1.17F
): TextStyle {
    return TextStyle(
        fontSize = fontSize,
        lineHeight = (fontSize.value * lineHeightRatio).sp,
        letterSpacing = 0.sp
    )
}
