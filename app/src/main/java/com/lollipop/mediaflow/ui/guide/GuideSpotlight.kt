package com.lollipop.mediaflow.ui.guide

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.ui.effect.FloatingSurfaceElevation
import com.lollipop.mediaflow.ui.effect.effectSurfaceSolid
import kotlin.math.roundToInt

/** 气泡内「标题」与「正文」的间距。 */
private val TitleGap = 6.dp

/** 气泡内「正文」与「脚注」的间距；比 [TitleGap] 大，让脚注与正文拉开层次。 */
private val FooterGap = 12.dp

/** 脚注（点击提示）的不透明度：弱化处理，不与正文抢视线。 */
private const val FooterAlpha = 0.6F

/**
 * 引导遮罩（纯视觉层）。
 *
 * ## 它做什么 / 不做什么
 *
 * 只负责「把窗口暗下来、在目标处挖一个洞、把气泡摆在洞旁边、整层吃掉点击」。
 * 它**不知道**该不该展示、也不碰任何持久化——那些属于页面与 [GuideHost]。
 *
 * ## 挖洞为什么用「离屏图层 + Clear」而不是叠色
 *
 * 若把变暗做成「四块矩形围出中间空白」，洞内的内容虽然可见，但**遮罩边缘与洞之间
 * 不会形成任何压力关系**，观感是「贴了四张黑纸」；而这里在同一个离屏图层内先铺满变暗，
 * 再以 [BlendMode.Clear] 把洞处的像素**真正擦除**，洞内是**完全未被处理过的原始画面**，
 * 洞外才是被压暗的区域——这才是聚光灯的观感来源。
 *
 * 之所以必须 `CompositingStrategy.Offscreen`：`Clear` 需要一层独立的缓冲来擦，
 * 否则会把窗口背景一并擦穿。该离屏缓冲是全屏尺寸，属于「展示期间的一次性开销」，
 * 与引导作为一次性功能的定位相符。
 *
 * ## 就绪前不出画面
 *
 * 气泡尺寸未测量、或窗口尺寸未知时，整层保持**不可见且不响应点击**
 * （与 [`RememberFlowPlayer`](app/src/main/java/com/lollipop/mediaflow/playback/RememberFlowPlayer.kt:108)
 * 对「尺寸未就绪不出画面」的处理一致）。这样气泡不会先出现在错误位置再跳到正确位置。
 *
 * @param step 当前步骤（提供文案、洞形状、气泡偏好方向）。
 * @param holeBounds 洞在**窗口坐标**下的外框（已含留白），来自 [GuideAnchorRegistry]。
 * @param onNext 用户点击任意位置时回调；由 [GuideHost] 决定是推进还是收尾。
 */
@Composable
fun GuideSpotlight(
    step: GuideStep,
    holeBounds: Rect,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val windowSize = LocalWindowInfo.current.containerSize
    val safeDrawing = WindowInsets.safeDrawing.asPaddingValues()

    // 气泡的可用区域：窗口内缩去系统栏与手势区，避免气泡压在状态栏 / 导航条上
    val bubbleArea = with(density) {
        Rect(
            left = safeDrawing.calculateLeftPadding(layoutDirection).toPx(),
            top = safeDrawing.calculateTopPadding().toPx(),
            right = windowSize.width - safeDrawing.calculateRightPadding(layoutDirection).toPx(),
            bottom = windowSize.height - safeDrawing.calculateBottomPadding().toPx(),
        )
    }

    val scrimColor = GuideOverlayStyle.scrimColor()
    val borderColor = GuideOverlayStyle.holeBorderColor()
    val strokeWidth = with(density) { GuideOverlayStyle.HoleStrokeWidth.toPx() }
    val gap = with(density) { GuideOverlayStyle.BubbleGap.toPx() }

    val holeCornerRadius = GuideOverlayStyle.holeCornerRadius(
        shape = step.holeShape,
        holeWidth = with(density) { holeBounds.width.toDp() },
        holeHeight = with(density) { holeBounds.height.toDp() },
    )
    val holeCornerRadiusPx = with(density) { holeCornerRadius.toPx() }

    // 换步时必须重新测量：沿用上一步的尺寸会把气泡摆到错误位置
    var bubbleSize by remember(step) { mutableStateOf(IntSize.Zero) }
    val isReady = bubbleSize.width > 0 && bubbleSize.height > 0 &&
        windowSize.width > 0 && windowSize.height > 0

    val bubbleOffset = if (isReady) {
        placeBubble(
            hole = holeBounds,
            bubble = Size(bubbleSize.width.toFloat(), bubbleSize.height.toFloat()),
            area = bubbleArea,
            gap = gap,
            preferred = step.bubbleSide,
        )
    } else {
        Offset.Zero
    }

    val interactionSource = remember { MutableInteractionSource() }
    val tapLabel = stringResource(id = R.string.guide_tap_anywhere)

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(if (isReady) 1F else 0F)
            .then(
                // 未就绪时不挂点击：否则会「吃掉一次点击却什么都不发生」
                if (isReady) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClickLabel = tapLabel,
                        onClick = onNext,
                    )
                } else {
                    Modifier
                }
            )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            // 1. 整窗压暗
            drawRect(color = scrimColor)
            // 2. 真正擦除洞内像素（Clear 会忽略源色），露出未被处理过的原始内容
            drawRoundRect(
                color = Color.Black,
                topLeft = Offset(holeBounds.left, holeBounds.top),
                size = Size(holeBounds.width, holeBounds.height),
                cornerRadius = CornerRadius(holeCornerRadiusPx, holeCornerRadiusPx),
                blendMode = BlendMode.Clear,
            )
            // 3. 描边必须画在 Clear 之后，否则会被一并擦掉
            drawRoundRect(
                color = borderColor,
                topLeft = Offset(holeBounds.left, holeBounds.top),
                size = Size(holeBounds.width, holeBounds.height),
                cornerRadius = CornerRadius(holeCornerRadiusPx, holeCornerRadiusPx),
                style = Stroke(width = strokeWidth),
            )
        }

        GuideBubble(
            step = step,
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = bubbleOffset.x.roundToInt(),
                        y = bubbleOffset.y.roundToInt(),
                    )
                }
                .onSizeChanged { size ->
                    bubbleSize = size
                },
        )
    }
}

/**
 * 气泡本体：标题 + 正文 + 点击提示。
 *
 * 外观复用悬浮元素的「纯色 + 投影 + 描边」统一外壳（[effectSurfaceSolid]），
 * 而**不**使用会采样模糊的 [com.lollipop.mediaflow.ui.effect.effectSurface]：
 * 本遮罩已确定不做模糊，气泡背后压着的是遮罩而非可采样内容。
 *
 * 修饰符顺序有意为之：`onSizeChanged` 在 `padding` **之外**，因此上报的是气泡外框尺寸
 * （含内边距），摆位与夹取都基于外框才准确。
 */
@Composable
private fun GuideBubble(
    step: GuideStep,
    modifier: Modifier = Modifier,
) {
    val containerColor = GuideOverlayStyle.bubbleContainerColor()
    val contentColor = GuideOverlayStyle.bubbleContentColor()
    Column(
        modifier = modifier
            .widthIn(max = GuideOverlayStyle.BubbleMaxWidth)
            .effectSurfaceSolid(
                shape = RoundedCornerShape(GuideOverlayStyle.BubbleCornerRadius),
                fallbackColor = containerColor,
                shadowElevation = FloatingSurfaceElevation,
            )
            .padding(GuideOverlayStyle.BubblePadding)
    ) {
        Text(
            text = step.title,
            color = contentColor,
            fontSize = GuideOverlayStyle.TitleFontSize,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(TitleGap))
        Text(
            text = step.description,
            color = contentColor,
            fontSize = GuideOverlayStyle.BodyFontSize,
        )
        Spacer(modifier = Modifier.height(FooterGap))
        Text(
            text = stringResource(id = R.string.guide_tap_anywhere),
            color = contentColor.copy(alpha = FooterAlpha),
            fontSize = GuideOverlayStyle.FooterFontSize,
        )
    }
}

/**
 * 计算气泡左上角位置（窗口坐标 px）。
 *
 * 策略：按偏好方向与其后的默认顺序（下 → 上 → 右 → 左）取**第一个能完整放下**的方向；
 * 若四个方向都放不下（极端窄屏或目标占满屏幕），则退化为「可用空间最大的方向」并夹取进
 * 可放置区域——宁可让气泡与洞部分重叠，也不要把它挤出屏幕之外。
 */
private fun placeBubble(
    hole: Rect,
    bubble: Size,
    area: Rect,
    gap: Float,
    preferred: GuideBubbleSide,
): Offset {
    val order = bubbleOrder(preferred)
    order.forEach { side ->
        val offset = idealBubbleOffset(side, hole, bubble, gap)
        if (fitsInArea(offset, bubble, area)) {
            return offset
        }
    }
    val fallback = order.maxByOrNull { side -> availableLength(side, hole, area, gap) } ?: order.first()
    return clampToArea(idealBubbleOffset(fallback, hole, bubble, gap), bubble, area)
}

/** 偏好方向优先，其余方向按默认顺序补足。 */
private fun bubbleOrder(preferred: GuideBubbleSide): List<GuideBubbleSide> {
    val defaults = listOf(
        GuideBubbleSide.Below,
        GuideBubbleSide.Above,
        GuideBubbleSide.End,
        GuideBubbleSide.Start,
    )
    if (preferred == GuideBubbleSide.Auto) {
        return defaults
    }
    return listOf(preferred) + defaults.filter { it != preferred }
}

/** 某方向下的理想位置：垂直于该方向的轴与洞居中对齐。 */
private fun idealBubbleOffset(
    side: GuideBubbleSide,
    hole: Rect,
    bubble: Size,
    gap: Float,
): Offset {
    return when (side) {
        GuideBubbleSide.Auto, GuideBubbleSide.Below -> {
            Offset(hole.center.x - bubble.width / 2F, hole.bottom + gap)
        }
        GuideBubbleSide.Above -> {
            Offset(hole.center.x - bubble.width / 2F, hole.top - gap - bubble.height)
        }
        GuideBubbleSide.Start -> {
            Offset(hole.left - gap - bubble.width, hole.center.y - bubble.height / 2F)
        }
        GuideBubbleSide.End -> {
            Offset(hole.right + gap, hole.center.y - bubble.height / 2F)
        }
    }
}

/** 气泡外框是否完整落在可放置区域内。 */
private fun fitsInArea(offset: Offset, size: Size, area: Rect): Boolean {
    return offset.x >= area.left && offset.y >= area.top &&
        offset.x + size.width <= area.right && offset.y + size.height <= area.bottom
}

/** 某方向从洞边缘到可放置区域边界的可用长度。 */
private fun availableLength(side: GuideBubbleSide, hole: Rect, area: Rect, gap: Float): Float {
    return when (side) {
        GuideBubbleSide.Auto, GuideBubbleSide.Below -> area.bottom - hole.bottom - gap
        GuideBubbleSide.Above -> hole.top - area.top - gap
        GuideBubbleSide.End -> area.right - hole.right - gap
        GuideBubbleSide.Start -> hole.left - area.left - gap
    }
}

/** 把气泡外框夹取进可放置区域内；区域小于气泡时贴左上角，保证内容仍可读。 */
private fun clampToArea(offset: Offset, size: Size, area: Rect): Offset {
    val maxX = (area.right - size.width).coerceAtLeast(area.left)
    val maxY = (area.bottom - size.height).coerceAtLeast(area.top)
    return Offset(
        x = offset.x.coerceIn(area.left, maxX),
        y = offset.y.coerceIn(area.top, maxY),
    )
}
