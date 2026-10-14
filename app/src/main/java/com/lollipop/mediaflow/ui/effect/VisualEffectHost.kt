package com.lollipop.mediaflow.ui.effect

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.lollipop.mediaflow.tools.Preferences
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState

/**
 * 读取当前特效参数快照（**非 Compose 环境**可用，例如 Dialog / Window 配置）。
 *
 * 使用缓存的偏好值（`get()`），因此**不会订阅变化**；UI 层请使用 [rememberVisualEffectParams]。
 */
fun readVisualEffectParams(): VisualEffectParams {
    return VisualEffectParams(
        mode = VisualEffectCapability.clampMode(
            VisualEffectMode.findByKey(Preferences.visualEffectMode.get())
        ),
        performanceLevel = PerformanceLevel.findByKey(Preferences.effectPerformanceMode.get()),
        blurRadiusLevel = Level3.findByKey(Preferences.blurRadiusLevel.get()),
    )
}

/**
 * 读取当前特效参数快照（Compose 版本）。
 *
 * 直接读取各偏好的 [androidx.compose.runtime.State]，因此**模式 / 档位的变更会触发订阅方重组**。
 */
@Composable
fun rememberVisualEffectParams(): VisualEffectParams {
    val mode = VisualEffectCapability.clampMode(
        VisualEffectMode.findByKey(Preferences.visualEffectMode.state.value)
    )
    val performanceLevel = PerformanceLevel.findByKey(Preferences.effectPerformanceMode.state.value)
    val blurRadiusLevel = Level3.findByKey(Preferences.blurRadiusLevel.state.value)

    return remember(mode, performanceLevel, blurRadiusLevel) {
        VisualEffectParams(
            mode = mode,
            performanceLevel = performanceLevel,
            blurRadiusLevel = blurRadiusLevel,
        )
    }
}

/**
 * 创建（或复用）供 source / surface 共享的 [HazeState]。
 *
 * 无特效模式下返回 `null`——此时不创建任何 Haze 状态。
 */
@Composable
fun rememberVisualEffectHazeState(params: VisualEffectParams): HazeState? {
    if (params.isNone) {
        return null
    }
    return rememberHazeState()
}

/**
 * 特效上下文：参数快照 + 与内容层共享的 [HazeState]。
 *
 * 用于在较深的组合层级（如播放页控制层、页面级浮层）中免去逐层透传参数。
 */
class VisualEffectContext internal constructor(
    val params: VisualEffectParams,
    val hazeState: HazeState?,
)

/** 供子树读取的特效上下文；未提供时为 `null`（效果退化为纯色）。 */
val LocalVisualEffect = compositionLocalOf<VisualEffectContext?> { null }

/** 为子树提供特效上下文。 */
@Composable
fun ProvideVisualEffect(
    params: VisualEffectParams,
    hazeState: HazeState?,
    content: @Composable () -> Unit,
) {
    val context = remember(params, hazeState) {
        VisualEffectContext(params = params, hazeState = hazeState)
    }
    CompositionLocalProvider(
        LocalVisualEffect provides context,
        content = content,
    )
}

/** [effectSource] 的上下文版本；未提供上下文时不挂任何修饰符。 */
@Composable
fun Modifier.effectSourceFromContext(): Modifier {
    val context = LocalVisualEffect.current ?: return this
    return this.effectSource(params = context.params, hazeState = context.hazeState)
}

/**
 * [effectSurface] 的上下文版本。
 *
 * 未提供上下文时退化为 `NONE` 模式——**纯色 + 投影 + 描边**，不含模糊。
 *
 * @param shadowElevation 投影高度，透传给 [effectSurface]（投影颜色随明暗主题切换）。
 */
@Composable
fun Modifier.effectSurfaceFromContext(
    shape: RoundedCornerShape,
    fallbackColor: Color,
    shadowElevation: Dp,
): Modifier {
    val context = LocalVisualEffect.current
    return this.effectSurface(
        params = context?.params ?: NoEffectParams,
        hazeState = context?.hazeState,
        shape = shape,
        fallbackColor = fallbackColor,
        shadowElevation = shadowElevation,
    )
}

/**
 * 「无特效」参数：未提供 [ProvideVisualEffect] 时的退化形态。
 *
 * 与 `NONE` 模式等价（纯色 + 投影 + 描边），保证浮层在缺少上下文时仍有基本层次。
 */
private val NoEffectParams = VisualEffectParams(
    mode = VisualEffectMode.NONE,
    performanceLevel = PerformanceLevel.AUTO,
    blurRadiusLevel = Level3.MEDIUM,
)
