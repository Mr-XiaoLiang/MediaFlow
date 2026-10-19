package com.lollipop.mediaflow.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.mediaflow.ui.effect.FloatingSurfaceElevation
import com.lollipop.mediaflow.ui.effect.effectSurfaceFromContext
import com.lollipop.mediaflow.ui.theme.currentThemeColor
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Slogan 图标尺寸，对齐 View 版 item_home_slogan（128dp x 42dp，内部上下留 4dp）。 */
private val SloganIconWidth = 128.dp
private val SloganIconHeight = 34.dp

/** 胶囊最小高度（单行）。悬浮布局下，列表顶部让位以此（或实测高度）为基准。 */
internal val SloganBarMinHeight = 42.dp

/** 胶囊圆角：固定为单行高度的一半（21dp），内容折行变高后圆角视觉保持一致。 */
private val SloganBarCornerRadius = SloganBarMinHeight / 2

/** 胶囊形状：固定圆角，避免使用 CircleShape 时圆角随高度变大。 */
private val SloganBarShape = RoundedCornerShape(SloganBarCornerRadius)

/** 胶囊左右内边距：让图标不贴到胶囊的圆角上。 */
private val SloganBarPaddingHorizontal = 12.dp

/** Slogan 文本的垂直内边距：多行时不至于贴住上下圆角边。 */
private val SloganBarPaddingVertical = 10.dp

/** Tab 轮廓线高亮描边宽度。 */
private val TabOutlineStrokeWidth = 3.dp

/** 更新提示光晕：完整一圈的周期（从每圈开始计时）。 */
private const val UpdateSweepPeriodMillis = 5_000L

/** 更新提示光晕：单圈时长。越短越灵动。 */
private const val UpdateSweepDurationMillis = 1_200

/** 更新提示光晕：每次连续转动的圈数。 */
private const val UpdateSweepTurnCount = 2

/** 更新提示光晕：光带最短 / 最长长度占胶囊周长的比例（随角度周期伸缩）。 */
private const val UpdateSweepTailMinFraction = 0.14F
private const val UpdateSweepTailMaxFraction = 0.42F

/** 更新提示光晕：光带沿弧长切分的段数，段数越多渐隐越平滑。 */
private const val UpdateSweepSegments = 36

/** 更新提示光晕：相邻段的重叠倍数，避免段与段之间出现断点。 */
private const val UpdateSweepSegmentOverlap = 1.8F

/** 更新提示光晕：芯线基准宽度。 */
private val UpdateSweepBaseWidth = 2.2.dp

/** 更新提示光晕：整段动画首尾的长度展开 / 收缩占比。 */
private const val UpdateSweepRampFraction = 0.20F

/** 更新提示光晕：头部速度包络的最小 / 最大倍率（两端慢、中间快）。 */
private const val UpdateSweepMinSpeed = 0.35F
private const val UpdateSweepMaxSpeed = 1.65F

/**
 * 更新提示光晕的起点偏移（占周长比例）。
 *
 * 轮廓路径以 3 点钟为 0 点顺时针构建，顺时针走 3/4 圈正好到 12 点钟。
 * 光晕偏移到这里后，启动 / 消失都发生在顶边直线段上，不会被圆角扭曲。
 * Tab 指示弧仍使用原始 3 点钟起点，不受此偏移影响。
 */
private const val UpdateSweepStartOffsetFraction = 0.75F

/**
 * 顶部悬浮 Slogan 胶囊（纯 Compose）。
 *
 * 由于移动端首页改为「单条悬浮栏 + 轮廓线指示」，本组件承担两件事：
 *
 * 1. **内容**：自定义文本优先，否则展示 App 图标；点击展开操作气泡菜单（由上层锚定）。
 * 2. **Tab 指示**：沿胶囊边缘绘制一段与主题色一致的轮廓线高亮，长度 = 周长 / 分页数；
 *    起点固定在时钟 3 点钟位置，顺时针推进，连续跟随 [pagerState] 滑动到下一格。
 *    隐私关闭时 2 页（各 50%），隐私开启时 4 页（各 25%），仅绘制高亮段、不画底轨。
 *
 * 交互只有轻点与长按：轻点回调 [onClick]（展开菜单），长按回调 [onLongClick]（切换隐私）。
 * 「回到顶部」不再由本组件承载，改由宿主页面右下角的悬浮按钮负责（见 HomeScreen）。
 *
 * [onBarHeightChanged] 上报胶囊实测高度，供上层给列表顶部留出等量空隙，避免与悬浮栏重叠。
 *
 * [reloadTick] 变化时会重新读取偏好设置，保证从设置页返回后 Slogan 及时更新。
 */
@Composable
fun SloganBar(
    pagerState: PagerState,
    pageCount: Int,
    reloadTick: Int,
    hasUpdate: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onBarHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val customValue = remember(reloadTick) {
        Preferences.customSloganValue.get()
    }
    val themeColor = currentThemeColor()
    // 光晕颜色随明暗切换，方向与“文字强调色”相反：
    // - 浅色背景需要亮：取主题亮青（buttonSlider 去掉 alpha），避免向黑加深后发黑；
    // - 深色背景需要浓：取浓郁青绿（buttonTextPrimary），在黑底上更有存在感。
    val updateSweepColor = if (isSystemInDarkTheme()) {
        themeColor.buttonTextPrimary
    } else {
        themeColor.buttonSlider.copy(alpha = 1F)
    }
    val updateSweepProgress = remember { Animatable(0F) }
    val density = LocalDensity.current

    // 回调可能随重组变化，用 rememberUpdatedState 保证回调里读到的始终是最新引用
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentOnBarHeightChanged by rememberUpdatedState(onBarHeightChanged)

    val strokeWidthPx = with(density) { TabOutlineStrokeWidth.toPx() }

    // 有更新时：立即开始一段“旋转 + 伸缩”动画，之后严格每 5 秒重复。
    // 扫描弧是独立的叠加层，不参与页码计算；协程随组件离开组合自动取消。
    LaunchedEffect(hasUpdate) {
        updateSweepProgress.snapTo(0F)
        if (!hasUpdate) {
            return@LaunchedEffect
        }
        while (true) {
            updateSweepProgress.snapTo(0F)
            // 总进度线性推进；每圈内部的快慢由绘制阶段的 FastOutSlowIn 换算，
            // 这样启动 / 消失时长度可以从 0 连续展开、再收回到 0。
            updateSweepProgress.animateTo(
                targetValue = 1F,
                animationSpec = tween(
                    durationMillis = UpdateSweepDurationMillis * UpdateSweepTurnCount,
                    easing = LinearEasing
                )
            )
            val sweepDuration = UpdateSweepDurationMillis.toLong() * UpdateSweepTurnCount
            delay((UpdateSweepPeriodMillis - sweepDuration).coerceAtLeast(0L))
        }
    }

    Box(
        modifier = modifier.padding(horizontal = 16.dp)
    ) {
        // 胶囊本体：决定整个 Box 的尺寸；轮廓线高亮随 [pagerState] 在绘制阶段实时刷新
        Box(
            modifier = Modifier
                .heightIn(min = SloganBarMinHeight)
                // 不设最大宽度：短文本贴合内容并收缩为正圆，长文本自然撑满可用宽度后换行
                .widthIn(min = SloganBarMinHeight)
                .onSizeChanged { currentOnBarHeightChanged(it.height) }
                // 更新光晕画在胶囊背景之下、裁剪之外：
                // 中心沿轮廓、半径大于胶囊厚度，外溢部分形成真正的边缘外发光，
                // 不会被 effectSurface 内部的 clip(shape) 切掉。
                .drawBehind {
                    drawUpdateSweep(
                        progress = updateSweepProgress.value,
                        cornerRadius = SloganBarCornerRadius.toPx(),
                        baseWidth = UpdateSweepBaseWidth.toPx(),
                        color = updateSweepColor
                    )
                }
                // 投影 + 描边统一由 effectSurface 提供（高度为全局唯一常量，颜色随明暗主题切换）
                .effectSurfaceFromContext(
                    shape = SloganBarShape,
                    fallbackColor = themeColor.windowBackground,
                    shadowElevation = FloatingSurfaceElevation,
                )
                .combinedClickable(
                    onClick = { currentOnClick() },
                    onLongClick = { currentOnLongClick() }
                )
                .drawWithContent {
                    drawContent()
                    drawTabOutlineHighlight(
                        // 连续页码：整数代表完全落在某一页，小数代表滑动过程中的页间位置。
                        position = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                        pageCount = pageCount,
                        cornerRadius = SloganBarCornerRadius.toPx(),
                        strokeWidth = strokeWidthPx,
                        // 与主题色一致：取主题强调色
                        color = themeColor.buttonSlider
                    )
                }
                .padding(horizontal = SloganBarPaddingHorizontal),
            contentAlignment = Alignment.CenterStart
        ) {
            if (customValue.isNotEmpty()) {
                Text(
                    text = customValue,
                    color = themeColor.buttonText,
                    style = plainTextStyle(16.sp),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = SloganBarPaddingVertical)
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_mediaflow),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(themeColor.buttonText),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(SloganIconWidth, SloganIconHeight)
                )
            }
        }
    }
}

/**
 * 绘制 Tab 轮廓线高亮。
 *
 * 以胶囊（圆角矩形）周长做参数化：起点为时钟 3 点钟位置，顺时针推进；第 i 个分页对应
 * `[i / pageCount, (i + 1) / pageCount]` 段的周长区间。[position] 为连续页码，滑动时高亮段
 * 随之在轮廓上平滑滚动。描边向内侧缩半个线宽，保证整段落在胶囊内部。
 */
private fun DrawScope.drawTabOutlineHighlight(
    position: Float,
    pageCount: Int,
    cornerRadius: Float,
    strokeWidth: Float,
    color: Color
) {
    if (pageCount <= 0 || size.width <= 0F || size.height <= 0F) {
        return
    }
    val inset = strokeWidth / 2F
    val left = inset
    val top = inset
    val right = size.width - inset
    val bottom = size.height - inset
    if (right <= left || bottom <= top) {
        return
    }
    // 同心圆角：内侧圆角 = 外侧圆角 - 内缩量，并夹紧到当前尺寸允许的最大圆角
    val maxRadius = min(right - left, bottom - top) / 2F
    val radius = min(cornerRadius - inset, maxRadius).coerceAtLeast(0F)

    val outline = buildRoundedRectOutline(left, top, right, bottom, radius)
    val measure = PathMeasure()
    measure.setPath(outline, false)
    val length = measure.length
    if (length <= 0F) {
        return
    }

    val segmentLength = length / pageCount
    val clamped = position.coerceIn(0F, (pageCount - 1).toFloat())
    val start = clamped / pageCount * length
    val stop = min(start + segmentLength, length)

    val highlight = Path()
    measure.getSegment(start, stop, highlight, startWithMoveTo = true)
    drawPath(
        path = highlight,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}

/**
 * 绘制“有新版本”提示的环绕光晕。
 *
 * 与 Tab 指示弧**职责分离**：Tab 弧回答“当前在第几页”，位置由 [pagerState] 驱动；
 * 光晕只回答“有新版本”，独立绕周长运动，不参与页码计算，因此滑动 Pager 时不会错位。
 *
 * 形状是**细长光带**，而不是圆形光斑：沿弧长切段，每段叠三层同心描边
 * （外层大晕 → 中层柔晕 → 内层芯线），宽度始终只有几 dp；alpha 按 `(1 - t)^1.8`
 * 逐段衰减，尾端自然消融。路径整体向外扩，画在 drawBehind（背景之下）时不会被
 * 胶囊底色遮住，形成贴边的外发光。
 *
 * [progress] 为 0~1 的整圈进度：0 / 1 时完全不绘制（不构建路径，静止时零开销）。
 */
private fun DrawScope.drawUpdateSweep(
    progress: Float,
    cornerRadius: Float,
    baseWidth: Float,
    color: Color
) {
    if (progress <= 0F || progress >= 1F) {
        return
    }
    if (size.width <= 0F || size.height <= 0F) {
        return
    }
    // 中线直接贴着胶囊边缘：描边外半圈露出成环绕光线，内半圈被底色自然遮住
    val left = 0F
    val top = 0F
    val right = size.width
    val bottom = size.height
    if (right <= left || bottom <= top) {
        return
    }
    val maxRadius = min(right - left, bottom - top) / 2F
    val radius = min(cornerRadius, maxRadius).coerceAtLeast(0F)
    val outline = buildRoundedRectOutline(left, top, right, bottom, radius)
    val measure = PathMeasure()
    measure.setPath(outline, false)
    val length = measure.length
    if (length <= 0F) {
        return
    }
    // 头部速度包络：两端慢、中间快，但始终为正，头部不会停住。
    // 对 speed(p) = min + (max - min) * sin(πp) 积分得到角度，保证角度连续且单调。
    val speedRange = UpdateSweepMaxSpeed - UpdateSweepMinSpeed
    val rawHeadPhase = UpdateSweepMinSpeed * progress -
        (speedRange / PI.toFloat()) * cos(progress * PI.toFloat())
    val rawStart = -speedRange / PI.toFloat()
    val rawEnd = UpdateSweepMinSpeed + speedRange / PI.toFloat()
    val headPhase = (rawHeadPhase - rawStart) / (rawEnd - rawStart)
    val headTurns = headPhase * UpdateSweepTurnCount

    // 长度包络：开始时 0 → 正常，结束时正常 → 0。
    // 启动时头部继续前进，尾部从头部位置被“拉开”；结束时尾部加速追上头部。
    val startRamp = (progress / UpdateSweepRampFraction).coerceIn(0F, 1F)
    val endRamp = ((1F - progress) / UpdateSweepRampFraction).coerceIn(0F, 1F)
    val envelope = min(startRamp, endRamp)
    // 中段再做正弦伸缩：两圈一个完整周期（第一圈拉长、第二圈缩短）。
    // 变化率相对 headTurns 低于 1，保证尾部也始终向前，不会出现“尾巴倒退”。
    val lengthWave = (sin(headTurns * PI.toFloat()) + 1F) / 2F
    val tailFraction = (
        UpdateSweepTailMinFraction +
            (UpdateSweepTailMaxFraction - UpdateSweepTailMinFraction) * lengthWave
        ) * envelope
    val tailLength = length * tailFraction
    // 光晕范围跟随长度：越长越宽越亮，越短越细越淡。
    // 用幂函数而不是线性映射：长度较短时仍保留大部分可见度，
    // 只有接近 0 时才快速衰减，避免前半段与收缩后半段“几乎看不见”。
    val lengthRatio = (tailFraction / UpdateSweepTailMaxFraction).coerceIn(0F, 1F)
    val glowIntensity = lengthRatio.pow(0.45F)
    val glowWidthScale = 0.38F + 0.62F * glowIntensity
    val startOffset = length * UpdateSweepStartOffsetFraction
    val head = (startOffset + length * (headTurns % 1F)) % length
    val step = tailLength / UpdateSweepSegments
    val segmentLength = step * UpdateSweepSegmentOverlap
    val path = Path()

    for (index in 0 until UpdateSweepSegments) {
        // t：0 靠近头部，1 为尾端；强度按幂函数衰减，尾部边缘自然消失
        val t = (index + 1F) / UpdateSweepSegments
        val strength = (1F - t).coerceAtLeast(0F).pow(1.8F)
        if (strength <= 0.01F) {
            continue
        }
        val start = head - tailLength * t
        val segmentAlpha = strength
        // 三层同心描边：外层大晕、中层柔晕、内层芯线。
        // 宽度只有几 dp，所以整体是一条细长光带，而不是一团圆光。
        if (strength > 0.42F) {
            drawOutlineSegment(
                measure = measure,
                start = start,
                segmentLength = segmentLength,
                color = color,
                strokeWidth = baseWidth * 4.2F * glowWidthScale,
                alpha = segmentAlpha * 0.12F * glowIntensity,
                path = path
            )
        }
        if (strength > 0.16F) {
            drawOutlineSegment(
                measure = measure,
                start = start,
                segmentLength = segmentLength,
                color = color,
                strokeWidth = baseWidth * 2.4F * glowWidthScale,
                alpha = segmentAlpha * 0.24F * glowIntensity,
                path = path
            )
        }
        drawOutlineSegment(
            measure = measure,
            start = start,
            segmentLength = segmentLength,
            color = color,
            strokeWidth = baseWidth * (0.35F + 0.65F * glowIntensity),
            alpha = segmentAlpha * 0.85F * glowIntensity,
            path = path
        )
    }
}

/**
 * 绘制轮廓上的一段弧，支持跨过 3 点钟起点。
 *
 * `PathMeasure.getSegment()` 不会自动环绕：区间越过周长终点时，必须拆成
 * `[start, length]` 与 `[0, 溢出部分]` 两段，否则每圈结尾会丢失一小截。
 *
 * [path] 由调用方复用，避免逐段分配 Path 造成每帧 GC 压力。
 */
private fun DrawScope.drawOutlineSegment(
    measure: PathMeasure,
    start: Float,
    segmentLength: Float,
    color: Color,
    strokeWidth: Float,
    alpha: Float,
    path: Path
) {
    if (segmentLength <= 0F || alpha <= 0F) {
        return
    }
    val total = measure.length
    if (total <= 0F) {
        return
    }
    val normalizedStart = ((start % total) + total) % total
    val segmentEnd = normalizedStart + segmentLength
    if (segmentEnd <= total) {
        path.reset()
        measure.getSegment(normalizedStart, segmentEnd, path, true)
        drawPath(
            path = path,
            color = color,
            alpha = alpha,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        return
    }
    path.reset()
    measure.getSegment(normalizedStart, total, path, true)
    drawPath(
        path = path,
        color = color,
        alpha = alpha,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
    path.reset()
    measure.getSegment(0F, segmentEnd - total, path, true)
    drawPath(
        path = path,
        color = color,
        alpha = alpha,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}

/**
 * 构造圆角矩形轮廓 [Path]，起点为时钟 3 点钟位置（右边中点），顺时针闭合。
 *
 * 之所以手工构造而非使用 `addRoundRect`：后者起点在左上角，无法直接得到 3 点钟起点。
 */
private fun buildRoundedRectOutline(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float
): Path {
    val path = Path()
    val centerY = (top + bottom) / 2F
    path.moveTo(right, centerY)
    // 右边缘向下
    path.lineTo(right, bottom - radius)
    path.arcTo(
        rect = Rect(right - 2 * radius, bottom - 2 * radius, right, bottom),
        startAngleDegrees = 0F,
        sweepAngleDegrees = 90F,
        forceMoveTo = false
    )
    // 底边向左
    path.lineTo(left + radius, bottom)
    path.arcTo(
        rect = Rect(left, bottom - 2 * radius, left + 2 * radius, bottom),
        startAngleDegrees = 90F,
        sweepAngleDegrees = 90F,
        forceMoveTo = false
    )
    // 左边缘向上
    path.lineTo(left, top + radius)
    path.arcTo(
        rect = Rect(left, top, left + 2 * radius, top + 2 * radius),
        startAngleDegrees = 180F,
        sweepAngleDegrees = 90F,
        forceMoveTo = false
    )
    // 顶边向右
    path.lineTo(right - radius, top)
    path.arcTo(
        rect = Rect(right - 2 * radius, top, right, top + 2 * radius),
        startAngleDegrees = 270F,
        sweepAngleDegrees = 90F,
        forceMoveTo = false
    )
    // 回到 3 点钟起点
    path.lineTo(right, centerY)
    path.close()
    return path
}
