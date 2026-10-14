package com.lollipop.mediaflow.ui.effect

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource

/**
 * 内容层标记：声明「模糊数据来源」。
 *
 * 仅承载**内容展示**的部分挂此修饰符；无特效时不挂任何修饰符。
 * 与承载效果的 [effectSurface] 必须是**兄弟层级**，不可交叉。
 */
@Composable
fun Modifier.effectSource(params: VisualEffectParams, hazeState: HazeState?): Modifier {
    if (params.isNone || hazeState == null) {
        return this
    }
    return this.hazeSource(hazeState)
}

/**
 * 悬浮元素的背景特效。
 *
 * - `NONE`：**不透明纯色**（[fallbackColor]）；
 * - `BLUR`：**先铺不透明底色**（与 `NONE` 相同的颜色），**再在其上叠加模糊**——
 *   因此按钮整体不透明、不透出背后内容，但表面带模糊质感。
 *
 * 模糊只使用「无色调」样式（仅半径 / 噪点），不叠加任何背景色或主题色调。
 * 形状裁剪先行（Haze 的绘制区域是矩形）。
 */
@Composable
fun Modifier.effectSurface(
    params: VisualEffectParams,
    hazeState: HazeState?,
    shape: RoundedCornerShape,
    fallbackColor: Color,
): Modifier {
    if (params.isNone || hazeState == null) {
        return this.background(color = fallbackColor, shape = shape)
    }
    return this
        .clip(shape)
        // ① 不透明底色
        .background(color = fallbackColor, shape = shape)
        // ② 模糊
        .blurSurface(params = params, hazeState = hazeState)
        // ③ 同色色调叠加：模糊输出本身是半透明的，若不叠这一层，控件会「接近全透明」、
        //    上方图标与文字看不清。叠一层与底色同色的高不透明度色块后，控件整体不透明，
        //    同时仍保留少量模糊质感。
        .background(color = fallbackColor.copy(alpha = TintAlpha), shape = shape)
}

/**
 * 模糊之上叠加的「同色色调」不透明度。
 *
 * 越接近 1 越实（模糊几乎看不见）；越接近 0 越透（图标与文字越难读）。
 * 取 0.6：既能让模糊明显透出，又保证内容可读；需要更实 / 更透时只改这一个值。
 */
private const val TintAlpha = 0.6F

/**
 * 模糊模式。
 *
 * - 不使用 `HazeMaterials` 系列预设材质（会按主题叠加色调，观感发红发绿）；
 * - **显式叠加与按钮底色同色的背景色**：仅靠模糊会让控件显得近乎透明、上方图标与文字
 *   看不清；同色染色后控件整体不透明、内容清晰可读。
 */
@Composable
private fun Modifier.blurSurface(
    params: VisualEffectParams,
    hazeState: HazeState,
): Modifier {
    val style = HazeBlurStyle {
        blurRadius(EffectTuning.blurRadius(params.blurRadiusLevel))
        // 显式关闭噪点：Haze 的默认样式自带噪点
        noiseFactor(0F)
    }
    return this.hazeBlur(
        input = HazeInput.Sources(hazeState),
        style = style,
        performanceMode = EffectTuning.performanceMode(params.performanceLevel),
    )
}
