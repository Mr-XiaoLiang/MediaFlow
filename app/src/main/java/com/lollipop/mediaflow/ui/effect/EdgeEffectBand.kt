package com.lollipop.mediaflow.ui.effect

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
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
 *
 * 取值由原先的 24dp 缩减为 12dp：原高度过大，渐变带深入内容区过多、对内容造成
 * 不必要的干扰；减半后仍保留可感知的渐隐过渡，同时减少对内容的遮挡。
 */
val EdgeBandFadeExtra = 12.dp

/** 边缘带所处的一侧。四边齐备，用于横屏（导航栏 / 挖孔位于侧边）时的系统栏模糊兼容。 */
enum class EdgeBand {
    /** 屏幕顶部（状态栏 / 刘海区域）。 */
    TOP,

    /** 屏幕底部（导航栏 / 手势条区域）。 */
    BOTTOM,

    /** 屏幕起始侧（LTR 为左；横屏时导航栏 / 挖孔所在侧）。 */
    START,

    /** 屏幕结束侧（LTR 为右；横屏时导航栏 / 挖孔所在侧）。 */
    END,
}

/** 遮罩中「完全不透明」区间所占的比例（其余部分用于渐隐）。 */
private const val MaskSolidFraction = 0.5F

/**
 * 边缘带的透明度遮罩（按 **alpha 通道**生效）。
 *
 * - 顶部带：屏幕顶端起 [MaskSolidFraction] 区间完全不透明，向下渐隐到透明；
 * - 底部带：方向相反；
 * - 起始侧 / 结束侧带：沿水平方向，分别从外缘向屏幕中心渐隐。
 */
private fun edgeMask(edge: EdgeBand): Brush {
    val stops = when (edge) {
        EdgeBand.TOP -> arrayOf(
            0F to Color.Black,
            MaskSolidFraction to Color.Black,
            1F to Color.Transparent,
        )

        EdgeBand.BOTTOM -> arrayOf(
            0F to Color.Transparent,
            (1F - MaskSolidFraction) to Color.Black,
            1F to Color.Black,
        )

        EdgeBand.START -> arrayOf(
            0F to Color.Black,
            MaskSolidFraction to Color.Black,
            1F to Color.Transparent,
        )

        EdgeBand.END -> arrayOf(
            0F to Color.Transparent,
            (1F - MaskSolidFraction) to Color.Black,
            1F to Color.Black,
        )
    }
    return when (edge) {
        EdgeBand.TOP, EdgeBand.BOTTOM -> Brush.verticalGradient(colorStops = stops)
        EdgeBand.START, EdgeBand.END -> Brush.horizontalGradient(colorStops = stops)
    }
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
 * 尺寸由调用方通过 [modifier] 指定：上下带横贯整宽、由调用方给出高度；
 * 左右带纵贯整高、由调用方给出宽度（见 [EdgeBand]）。
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
    // 上下带横贯整宽；左右带纵贯整高（另一维尺寸由调用方在 modifier 中指定）。
    val spanModifier = when (edge) {
        EdgeBand.TOP, EdgeBand.BOTTOM -> Modifier.fillMaxWidth()
        EdgeBand.START, EdgeBand.END -> Modifier.fillMaxHeight()
    }
    Box(
        modifier = modifier
            .then(spanModifier)
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
 *
 * 页面级「系统栏模糊」开关（[VisualEffectContext.systemBarBlurEnabled]）在此统一生效：
 * 为 `false` 时（如 Flow 页全屏）不渲染任何边缘带。该开关与 APP 特效总开关
 * （[VisualEffectParams.isNone]）构成逻辑与——两者都为真时才有模糊。
 *
 * 如此，各组件（首页 / 设置页 / 播放页外壳）无需再自行判断「是否全屏」等业务状态，
 * 业务差异由页面在 [ProvideVisualEffect] 处通过 [VisualEffectContext.systemBarBlurEnabled] 表达。
 */
@Composable
fun EdgeEffectBandFromContext(
    edge: EdgeBand,
    modifier: Modifier = Modifier,
) {
    val context = LocalVisualEffect.current ?: return
    if (!context.systemBarBlurEnabled) {
        return
    }
    EdgeEffectBand(
        params = context.params,
        hazeState = context.hazeState,
        edge = edge,
        modifier = modifier,
    )
}
