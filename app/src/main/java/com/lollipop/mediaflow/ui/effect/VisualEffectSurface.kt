package com.lollipop.mediaflow.ui.effect

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource

/**
 * **所有悬浮元素统一的投影高度**。
 *
 * 集中一处定义，避免各页面各自取值——历史上曾出现 4 / 8 / 16dp 三套，
 * 导致同类悬浮元素观感不一致。
 */
internal val FloatingSurfaceElevation = 8.dp

/**
 * **所有悬浮胶囊图标按钮（返回键 / 操作栏按钮）统一的几何尺寸**。
 *
 * 36dp 高、42dp 宽，即 `activity_flow.xml` 中 `menuBar` 与 `rotateBtn` 的尺寸。
 * 集中一处定义，保证各页面（分页 / 设置页 / 播放页）的返回键尺寸完全一致。
 */
internal val FloatingButtonWidth = 42.dp
internal val FloatingButtonHeight = 36.dp

/** 悬浮胶囊图标按钮的形状：高度一半的圆角，即胶囊。 */
internal val FloatingButtonShape = RoundedCornerShape(FloatingButtonHeight / 2)

/** 图标相对按钮的内缩内边距（保证矢量图标不被扣小）。 */
internal val FloatingButtonIconPadding = 6.dp

/** 悬浮元素描边宽度。 */
private val SurfaceBorderWidth = 1.dp

/** 描边不透明度：足以勾出边界，又不喧宾夺主。 */
private const val SurfaceBorderAlpha = 0.15F

/**
 * 暗色模式下的投影颜色不透明度。
 *
 * 亮色用黑色投影；而暗色背景本身是纯黑 / 近黑，黑投影叠上去**对比度为 0、完全看不见**。
 * 因此暗色改用低透明度白色投影来表达悬浮层次。
 */
private const val DarkShadowAlpha = 0.16F

/**
 * 模糊之上叠加的「同色色调」不透明度。
 *
 * 越接近 1 越实（模糊几乎看不见）；越接近 0 越透（图标与文字越难读）。
 * 取 0.6：既能让模糊明显透出，又保证内容可读；需要更实 / 更透时只改这一个值。
 */
private const val TintAlpha = 0.6F

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
 * 悬浮元素的统一外观：**投影 + 描边 + 底色 + 模糊**。
 *
 * 层级顺序（自下而上）：
 * 1. [surfaceShadow] 投影——**必须最先应用**，否则会被后续裁剪裁掉；
 * 2. [clip] 形状裁剪（Haze 的绘制区域是矩形）；
 * 3. **不透明底色**（与 `NONE` 模式相同的颜色）；
 * 4. 模糊（`BLUR` 模式）；
 * 5. **同色半透明色调**：模糊输出本身是半透明的，若不叠这一层，控件会「接近全透明」、
 *    上方图标与文字看不清；
 * 6. [surfaceBorder] 描边——压在最上层，勾出元素边界。
 *
 * `NONE` 模式（或上下文缺失）只走 1 / 2 / 3 / 6，即**不透明纯色 + 投影 + 描边**。
 * 模糊只使用「无色调」样式（仅半径 / 噪点），不叠加任何背景色或主题色调。
 *
 * @param shadowElevation 投影高度；投影与描边颜色随明暗主题自动切换
 *   （见 [surfaceShadow] / [surfaceBorder]），以解决「暗色下黑投影不可见」的问题。
 */
@Composable
fun Modifier.effectSurface(
    params: VisualEffectParams,
    hazeState: HazeState?,
    shape: RoundedCornerShape,
    fallbackColor: Color,
    shadowElevation: Dp,
): Modifier {
    val base = this
        .surfaceShadow(elevation = shadowElevation, shape = shape)
        .clip(shape)
        .background(color = fallbackColor, shape = shape)
    if (params.isNone || hazeState == null) {
        return base.surfaceBorder(shape = shape)
    }
    return base
        .blurSurface(params = params, hazeState = hazeState)
        .background(color = fallbackColor.copy(alpha = TintAlpha), shape = shape)
        .surfaceBorder(shape = shape)
}

/**
 * 悬浮元素的**无模糊**底色：纯色 + 投影 + 描边。
 *
 * 与 [effectSurface] 的唯一区别是不采样模糊。适用于**背后没有可采样内容**的悬浮按钮——
 * 例如固定顶栏里的返回键：它压在固定的页面底色上而非滚动内容之上，加模糊只是空操作。
 *
 * 投影 / 描边与 [effectSurface] 完全一致，因此这类按钮与其它悬浮元素观感保持统一。
 */
@Composable
fun Modifier.effectSurfaceSolid(
    shape: RoundedCornerShape,
    fallbackColor: Color,
    shadowElevation: Dp,
): Modifier {
    return this
        .surfaceShadow(elevation = shadowElevation, shape = shape)
        .clip(shape)
        .background(color = fallbackColor, shape = shape)
        .surfaceBorder(shape = shape)
}

/**
 * 悬浮元素统一投影。
 *
 * ⚠️ 顺序要求：**必须最先应用**（在 `clip` / `background` 之前），否则投影会被裁剪掉。
 *
 * 颜色随明暗主题切换：亮色黑投影；暗色低透明度白投影——黑投影在纯黑背景上不可见。
 * 注意 `ambientShadowColor` / `spotShadowColor` 仅在 API 28+ 生效，低版本仍是黑投影，
 * 暗色下的边界感由 [surfaceBorder] 兜底。
 */
@Composable
internal fun Modifier.surfaceShadow(elevation: Dp, shape: RoundedCornerShape): Modifier {
    if (elevation <= 0.dp) {
        return this
    }
    val shadowColor = if (isSystemInDarkTheme()) {
        Color.White.copy(alpha = DarkShadowAlpha)
    } else {
        Color.Black
    }
    return this.shadow(
        elevation = elevation,
        shape = shape,
        ambientColor = shadowColor,
        spotColor = shadowColor,
    )
}

/**
 * 悬浮元素统一描边。
 *
 * 暗色用低透明度白、亮色用低透明度黑，在明暗两种主题下都能勾出元素边界；
 * 同时也是低版本（API < 28，自定义投影颜色不生效）下主要的层次来源。
 */
@Composable
internal fun Modifier.surfaceBorder(shape: RoundedCornerShape): Modifier {
    val borderColor = if (isSystemInDarkTheme()) {
        Color.White.copy(alpha = SurfaceBorderAlpha)
    } else {
        Color.Black.copy(alpha = SurfaceBorderAlpha)
    }
    return this.border(width = SurfaceBorderWidth, color = borderColor, shape = shape)
}

/**
 * 模糊层。
 *
 * - 不使用 `HazeMaterials` 系列预设材质（会按主题叠加色调，观感发红发绿）；
 * - 显式关闭噪点：Haze 的默认样式自带噪点。
 */
@Composable
private fun Modifier.blurSurface(
    params: VisualEffectParams,
    hazeState: HazeState,
): Modifier {
    val style = HazeBlurStyle {
        blurRadius(EffectTuning.blurRadius(params.blurRadiusLevel))
        noiseFactor(0F)
    }
    return this.hazeBlur(
        input = HazeInput.Sources(hazeState),
        style = style,
        performanceMode = EffectTuning.performanceMode(params.performanceLevel),
    )
}
