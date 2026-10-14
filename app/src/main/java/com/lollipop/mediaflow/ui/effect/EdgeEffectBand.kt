package com.lollipop.mediaflow.ui.effect

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * 边缘带在系统栏 inset 之外**额外占据的高度**。
 *
 * 只在 inset 高度内做渐变，过渡区间过窄、几乎看不出「渐隐」；额外留出一段高度后，
 * 「朝内容中心渐隐」才有可见的展开空间。
 */
val EdgeBandFadeExtra = 24.dp

/** 边缘带所处的一侧。 */
enum class EdgeBand {
    /** 屏幕顶部（状态栏 / 刘海区域）。 */
    TOP,

    /** 屏幕底部（导航栏 / 手势条区域）。 */
    BOTTOM,
}

/** 遮罩中「完全不透明」区间所占的比例（其余部分用于渐隐）。 */
private const val MaskSolidFraction = 0.5F

/**
 * 边缘带的透明度遮罩（按 **alpha 通道**生效）。
 *
 * - 顶部带：屏幕顶端起 [MaskSolidFraction] 区间完全不透明，向下渐隐到透明；
 * - 底部带：方向相反。
 */
private fun edgeMask(edge: EdgeBand): Brush {
    val stops = if (edge == EdgeBand.TOP) {
        arrayOf(
            0F to Color.Black,
            MaskSolidFraction to Color.Black,
            1F to Color.Transparent,
        )
    } else {
        arrayOf(
            0F to Color.Transparent,
            (1F - MaskSolidFraction) to Color.Black,
            1F to Color.Black,
        )
    }
    return Brush.verticalGradient(colorStops = stops)
}

/**
 * 系统栏边缘带：在 WindowInsets 区域叠加的独立 surface。
 *
 * ## 渐变实现
 *
 * 使用 Haze **官方的样式遮罩**：`hazeBlur(style = HazeBlurStyle { mask(…) })`，
 * 由 Haze 自身按 mask 的 alpha 控制输出透明度——屏幕边缘完全显示模糊、朝内容中心渐隐。
 * （在 Haze 之外叠加 `DstIn` 遮罩是无效的：Haze 的输出不经过常规 `DrawScope`。）
 *
 * ## 渲染时机
 *
 * **仅 `BLUR` / `GLASS` 模式渲染**；`NONE` 模式**完全不创建**（不参与组合），
 * 避免在系统栏区域留下一条约等于「多加的纯色带」。
 * 玻璃模式下同样走模糊（边缘带不需要折射，且玻璃会扩展绘制边界影响遮罩）。
 *
 * 组件不挂任何点击 / 手势，纯绘制，避免吞掉边缘返回手势。
 * 高度由调用方通过 [modifier] 指定（通常取 inset 对应边 + [EdgeBandFadeExtra]）。
 */
@Composable
fun EdgeEffectBand(
    params: VisualEffectParams,
    hazeState: HazeState?,
    edge: EdgeBand,
    modifier: Modifier = Modifier,
) {
    // NONE（或无特效状态）：不渲染，避免多余的纯色带
    if (params.isNone || hazeState == null) {
        return
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(0.dp))
            .hazeBlur(
                input = HazeInput.Sources(hazeState),
                style = HazeBlurStyle {
                    blurRadius(EffectTuning.blurRadius(params.blurRadiusLevel))
                    // 显式关闭噪点：Haze 的默认样式自带噪点，会在系统栏区域留下颗粒
                    noiseFactor(0F)
                    mask(edgeMask(edge))
                },
                performanceMode = EffectTuning.performanceMode(params.performanceLevel),
            )
    )
}

/**
 * [EdgeEffectBand] 的上下文版本：从 [LocalVisualEffect] 读取参数，免去透传。
 */
@Composable
fun EdgeEffectBandFromContext(
    edge: EdgeBand,
    modifier: Modifier = Modifier,
) {
    val context = LocalVisualEffect.current ?: return
    EdgeEffectBand(
        params = context.params,
        hazeState = context.hazeState,
        edge = edge,
        modifier = modifier,
    )
}
