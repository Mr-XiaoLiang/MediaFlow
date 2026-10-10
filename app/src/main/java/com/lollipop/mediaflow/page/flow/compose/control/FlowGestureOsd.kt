package com.lollipop.mediaflow.page.flow.compose.control

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lollipop.mediaflow.tools.DisplayFormater
import kotlin.math.abs

/**
 * 手势 OSD（「放大镜」刻度尺）样式。
 *
 * 比例字段含义与 [com.lollipop.mediaflow.ui.GestureSlideOsdView] 的 `UiParams` 一一对应，
 * 默认值取其代码内默认（XML 未覆盖时的值）。
 */
data class FlowGestureOsdStyle(
    val color: Color = Color.White,
    /** 刻度线宽度（原 `lineWidth`，默认 10px）。 */
    val lineWidth: Dp = 5.dp,
    /** 刻度线数量（原 `stepCount`，默认 6）。 */
    val stepCount: Int = 6,
    /** 时间文字大小占高度的比例。 */
    val textSizeRatio: Float = 0.2F,
    /** 普通刻度线顶部占高度的比例。 */
    val defaultLineTopRatio: Float = 0.4F,
    /** 普通刻度线底部占高度的比例。 */
    val defaultLineBottomRatio: Float = 0.75F,
    /** 高亮（带时间文字）刻度线顶部占高度的比例。 */
    val highLineTopRatio: Float = 0.25F,
    /** 高亮刻度线底部占高度的比例。 */
    val highLineBottomRatio: Float = 0.75F,
    /** 时间文字基线占高度的比例。 */
    val textRatio: Float = 0.18F
) {

    companion object {
        val Default = FlowGestureOsdStyle()
    }
}

/**
 * 纯 Compose 的手势 OSD：横向刻度尺 + 时间文字 + 边缘淡出。
 *
 * 语义复刻 [com.lollipop.mediaflow.ui.GestureSlideOsdView]：
 * - 刻度以 [baseWeight] / [stepCount] 为步长（`stepTime`、`stepWidth`）；
 * - [precision] 越小 → 放大倍率 `1 / precision` 越大 → 刻度被"摊开"，形成放大镜观感；
 * - 每约 1/4 屏宽标注一次时间文字，其余刻度为空；
 * - 刻度透明度按「距最近边界的比例」渐隐（`2 × 最近距离 / 区间长度`）。
 *
 * ⚠️ 与 View 版的已知差异（已记入台账 M4-x）：View 版的 `touchX` 参数只被设置、并未参与绘制，
 * 因此本实现不再暴露该参数。
 */
@Composable
fun FlowGestureOsd(
    /** 视频总时长（毫秒）。 */
    totalDuration: Long,
    /** 当前播放进度（毫秒）。 */
    progressMs: Long,
    /** 基础权重（0.1F ~ 1F），决定一屏覆盖的比例。 */
    baseWeight: Float,
    /** 当前精度（0.01F ~ 1F）：越小越"精细"，刻度被放大。 */
    precision: Float,
    modifier: Modifier = Modifier,
    style: FlowGestureOsdStyle = FlowGestureOsdStyle.Default
) {
    if (totalDuration <= 0L) {
        return
    }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()

    val textStyle = remember(canvasSize.height, density, style.textSizeRatio) {
        TextStyle(fontSize = with(density) { (canvasSize.height * style.textSizeRatio).toSp() })
    }

    val stepCount = style.stepCount.coerceAtLeast(1)
    val stepTime = ((totalDuration * (baseWeight / stepCount)).toLong()).coerceAtLeast(1L)
    val stepWidth = if (canvasSize.width > 0) {
        canvasSize.width / stepCount
    } else {
        0
    }

    // 刻度字样：与 View 版 State.update 的生成规则一致（每 interval 个刻度放一个时间）
    val timeFlags = remember(totalDuration, stepTime, stepWidth, canvasSize.width, stepCount) {
        if (stepWidth <= 0) {
            emptyList()
        } else {
            val interval = ((canvasSize.width * 0.25F / stepWidth).toInt()).coerceAtLeast(1)
            buildList {
                var time = 0L
                var index = 0
                while (time < totalDuration) {
                    add(if (index % interval == 0) DisplayFormater.formatTime(time) else "")
                    time += stepTime
                    index++
                }
                if (time != totalDuration) {
                    add(DisplayFormater.formatTime(totalDuration))
                }
            }
        }
    }
    val textLayouts = remember(timeFlags, textStyle) {
        timeFlags.map { measurer.measure(text = it, style = textStyle) }
    }

    Canvas(
        // 尺寸由父级约束决定（通常由页面给出宽度与高度），这里只记录实际像素尺寸供刻度计算
        modifier = modifier.onSizeChanged { canvasSize = it }
    ) {
        if (size.width <= 0F || size.height <= 0F || timeFlags.isEmpty()) {
            return@Canvas
        }
        val viewWidth = size.width
        val viewHeight = size.height
        val weight = 1F / precision.coerceIn(0.01F, 1F)
        val currentStepWidth = stepWidth * weight
        val currentStepTime = (stepTime * weight).toLong().coerceAtLeast(1L)

        val centerX = viewWidth * 0.5F
        val weightProgress = (progressMs * weight).toLong()
        val leftIndex = weightProgress / currentStepTime
        val offsetX =
            ((weightProgress % currentStepTime) * 1F / currentStepTime * currentStepWidth * -1F)

        val halfFlagCount = ((centerX / currentStepWidth) + 1).toInt()
        val startIndex = (leftIndex - halfFlagCount).toInt()
        val startX = centerX + offsetX - (halfFlagCount * currentStepWidth)

        val lineWidthPx = style.lineWidth.toPx()
        val defaultTop = viewHeight * style.defaultLineTopRatio
        val defaultBottom = viewHeight * style.defaultLineBottomRatio
        val highTop = viewHeight * style.highLineTopRatio
        val highBottom = viewHeight * style.highLineBottomRatio
        val textBaselineY = viewHeight * style.textRatio

        val lineCount = halfFlagCount * 2 + 1
        val maxIndex = timeFlags.lastIndex
        for (i in 0 until lineCount) {
            val index = startIndex + i
            if (index < 0) {
                continue
            }
            if (index > maxIndex) {
                break
            }
            val lineX = startX + i * currentStepWidth
            val alpha = edgeAlpha(lineX, viewWidth)
            if (alpha <= 0F) {
                continue
            }
            val timeValue = timeFlags[index]
            val isHighlight = timeValue.isNotEmpty()
            drawLine(
                color = style.color.copy(alpha = style.color.alpha * alpha),
                start = Offset(lineX, if (isHighlight) highTop else defaultTop),
                end = Offset(lineX, if (isHighlight) highBottom else defaultBottom),
                strokeWidth = lineWidthPx
            )
            if (isHighlight && index < textLayouts.size) {
                val layout: TextLayoutResult = textLayouts[index]
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x = lineX - layout.size.width / 2F,
                        y = textBaselineY - layout.firstBaseline
                    ),
                    color = style.color.copy(alpha = style.color.alpha * alpha)
                )
            }
        }
    }
}

/**
 * 边缘渐隐：距最近边界的距离映射到 0..1（中心处最亮，边界处为 0）。
 *
 * 等价 View 版 `State.alpha(value, min, max)`：`2 × 最近距离 / 区间长度`。
 */
private fun edgeAlpha(value: Float, max: Float): Float {
    if (value !in 0F..max) {
        return 0F
    }
    if (max == 0F) {
        return 1F
    }
    val distToMin = value
    val distToMax = max - value
    val closest = abs(if (distToMin < distToMax) distToMin else distToMax)
    return (2F * closest / max).coerceIn(0F, 1F)
}
