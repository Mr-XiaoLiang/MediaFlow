package com.lollipop.mediaflow.page.flow.compose.control

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlin.math.abs

/** 触摸模式：拖拽（默认，需先横向超过 slop）或点击（按下即定位）。 */
enum class FlowSliderTouchMode {
    Tap,
    Drag
}

/** 滑块尺寸三件套（高度与两段之间的间隔）。 */
data class FlowSliderSize(
    val active: Dp,
    val inactive: Dp,
    val gap: Dp
)

/** 滑块颜色（已播放 / 未播放）。 */
data class FlowSliderColor(
    val active: Color,
    val inactive: Color
)

/**
 * 滑块样式：**默认态与触摸态两套值**，按下时按 [FlowSliderTouchAnimationMs] 线性插值。
 *
 * 对应 View 版 `DeconstructSlider.AnimationDelegate` 的 `default*` / `touched*` 两组参数。
 */
data class FlowSliderStyle(
    val defaultSize: FlowSliderSize,
    val touchedSize: FlowSliderSize,
    val defaultColor: FlowSliderColor,
    val touchedColor: FlowSliderColor
) {

    companion object {

        fun default(
            activeColor: Color,
            inactiveColor: Color,
            activeHeight: Dp = 10.dp,
            inactiveHeight: Dp = 5.dp,
            gap: Dp = 10.dp,
            touchedActiveHeight: Dp = activeHeight,
            touchedInactiveHeight: Dp = inactiveHeight,
            touchedGap: Dp = gap
        ): FlowSliderStyle {
            return FlowSliderStyle(
                defaultSize = FlowSliderSize(activeHeight, inactiveHeight, gap),
                touchedSize = FlowSliderSize(touchedActiveHeight, touchedInactiveHeight, touchedGap),
                defaultColor = FlowSliderColor(activeColor, inactiveColor),
                touchedColor = FlowSliderColor(activeColor, inactiveColor)
            )
        }
    }
}

/** 按下 / 抬起的尺寸与颜色过渡时长（对齐 View 版 `DURATION = 200L`）。 */
private const val FlowSliderTouchAnimationMs = 200

/**
 * 纯 Compose 的「解构式」进度滑块（复刻 [com.lollipop.common.ui.view.DeconstructSlider]）。
 *
 * 视觉：已播放段是较粗的胶囊（左端圆头），未播放段是较细的胶囊，两段之间留 [FlowSliderSize.gap]；
 * 触摸时按 [FlowSliderStyle] 在默认态与触摸态之间线性插值（原 `AnimationDelegate` 的行为）。
 *
 * 交互：与 View 版一致——
 * - [FlowSliderTouchMode.Drag]：横向超过 slop 才捕获并触发一次触感反馈（haptic），纵向超过 slop 则放弃；
 *   捕获后按**帧增量**累加进度；
 * - [FlowSliderTouchMode.Tap]：按下即按 x 定位，移动持续跟随。
 *
 * 进度是外部状态：本组件只上报 [onProgressChange]，由页面决定是否回写（拖动中通常先本地预览）。
 */
@Composable
fun FlowProgressSlider(
    /** 当前进度（0..1）。 */
    progress: Float,
    /** 用户操作导致的新进度（0..1）。 */
    onProgressChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    style: FlowSliderStyle = FlowSliderStyle.default(
        activeColor = Color.White,
        inactiveColor = Color(0x55FFFFFF)
    ),
    touchMode: FlowSliderTouchMode = FlowSliderTouchMode.Drag,
    onTouchDown: () -> Unit = {},
    onTouchUp: () -> Unit = {},
    enabled: Boolean = true
) {
    var isTouching by remember { mutableStateOf(false) }
    val touchFraction by animateFloatAsState(
        targetValue = if (isTouching) 1F else 0F,
        animationSpec = tween(durationMillis = FlowSliderTouchAnimationMs, easing = LinearEasing),
        label = "FlowProgressSliderTouch"
    )

    val currentProgress by rememberUpdatedState(progress)
    val currentOnProgressChange by rememberUpdatedState(onProgressChange)
    val currentOnTouchDown by rememberUpdatedState(onTouchDown)
    val currentOnTouchUp by rememberUpdatedState(onTouchUp)
    val haptics = LocalHapticFeedback.current

    val activeHeight = lerp(style.defaultSize.active, style.touchedSize.active, touchFraction)
    val inactiveHeight = lerp(style.defaultSize.inactive, style.touchedSize.inactive, touchFraction)
    val gapWidth = lerp(style.defaultSize.gap, style.touchedSize.gap, touchFraction)
    val activeColor = lerp(style.defaultColor.active, style.touchedColor.active, touchFraction)
    val inactiveColor = lerp(style.defaultColor.inactive, style.touchedColor.inactive, touchFraction)

    Canvas(
        modifier = modifier.pointerInput(enabled, touchMode, style) {
            if (!enabled) {
                return@pointerInput
            }
            val slop = viewConfiguration.touchSlop
            // 触摸几何按「默认态尺寸」计算：动画期间尺寸只变化几 px，采用默认态可避免每帧重算基准
            val activePx = style.defaultSize.active.toPx()
            val inactivePx = style.defaultSize.inactive.toPx()
            val decorationPx = (activePx + inactivePx) / 2F
            val sliderLeft = activePx / 2F
            val sliderLength = (size.width - decorationPx).coerceAtLeast(1F)

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var mode = SliderTouchState.Pending
                var lastX = down.position.x
                isTouching = true
                currentOnTouchDown()
                if (touchMode == FlowSliderTouchMode.Tap) {
                    currentOnProgressChange(progressOf(down.position.x, sliderLeft, sliderLength))
                }
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    val position = change.position
                    when (touchMode) {
                        FlowSliderTouchMode.Tap -> {
                            if (mode != SliderTouchState.Cancel) {
                                currentOnProgressChange(progressOf(position.x, sliderLeft, sliderLength))
                                change.consume()
                            }
                        }

                        FlowSliderTouchMode.Drag -> {
                            when (mode) {
                                SliderTouchState.Pending -> {
                                    val dx = abs(position.x - down.position.x)
                                    val dy = abs(position.y - down.position.y)
                                    if (dx > slop) {
                                        mode = SliderTouchState.Capture
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    } else if (dy > slop) {
                                        // 纵向优先：让位给翻页
                                        mode = SliderTouchState.Cancel
                                    }
                                }

                                SliderTouchState.Capture -> {
                                    val delta = (position.x - lastX) / sliderLength
                                    currentOnProgressChange(
                                        (currentProgress + delta).coerceIn(0F, 1F)
                                    )
                                }

                                SliderTouchState.Cancel -> {}
                            }
                            lastX = position.x
                            if (mode == SliderTouchState.Capture) {
                                change.consume()
                            }
                        }
                    }
                    if (!change.pressed) {
                        break
                    }
                }
                isTouching = false
                currentOnTouchUp()
            }
        }
    ) {
        val activePx = activeHeight.toPx()
        val inactivePx = inactiveHeight.toPx()
        val gapPx = gapWidth.toPx()
        val centerY = size.height / 2F

        // 已播放段：左端对齐内容起点，多出一个 activeHeight 作为圆头
        val progressFullWidth = size.width - activePx - inactivePx - gapPx
        val activeWidth = progressFullWidth * currentProgress.coerceIn(0F, 1F)
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(0F, centerY - activePx / 2F),
            size = Size((activeWidth + activePx).coerceAtLeast(0F), activePx),
            cornerRadius = CornerRadius(activePx / 2F)
        )

        // 未播放段：右端到内容终点
        val inactiveLeft = activeWidth + activePx + gapPx
        val inactiveWidth = (size.width - inactiveLeft).coerceAtLeast(0F)
        if (inactiveWidth > 0F) {
            drawRoundRect(
                color = inactiveColor,
                topLeft = Offset(inactiveLeft, centerY - inactivePx / 2F),
                size = Size(inactiveWidth, inactivePx),
                cornerRadius = CornerRadius(inactivePx / 2F)
            )
        }
    }
}

private fun progressOf(x: Float, sliderLeft: Float, sliderLength: Float): Float {
    return ((x - sliderLeft) / sliderLength).coerceIn(0F, 1F)
}

private enum class SliderTouchState {
    Pending,
    Capture,
    Cancel
}
