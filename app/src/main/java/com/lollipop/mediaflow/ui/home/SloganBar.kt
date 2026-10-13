package com.lollipop.mediaflow.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.mediaflow.ui.theme.currentThemeColor
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.min

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

/** 胶囊阴影高度，营造悬浮感。 */
private val SloganBarElevation = 8.dp

/** Tab 轮廓线高亮描边宽度。 */
private val TabOutlineStrokeWidth = 3.dp

/** 手势触发时胶囊下方弹出的圆形按钮：直径、与胶囊的间距。 */
private val ScrollTopFabSize = 56.dp
private val ScrollTopFabGap = 12.dp

/** fab 图标尺寸（比胶囊内容更大更醒目）。 */
private val ScrollTopFabIconSize = 32.dp

/**
 * 顶部悬浮 Slogan 胶囊（纯 Compose）。
 *
 * 由于移动端首页改为「单条悬浮栏 + 轮廓线指示」，本组件同时承担三件事：
 *
 * 1. **内容**：自定义文本优先，否则展示 App 图标；点击展开操作气泡菜单（由上层锚定）。
 * 2. **Tab 指示**：沿胶囊边缘绘制一段与主题色一致的轮廓线高亮，长度 = 周长 / 分页数；
 *    起点固定在时钟 3 点钟位置，顺时针推进，连续跟随 [pagerState] 滑动到下一格。
 *    隐私关闭时 2 页（各 50%），隐私开启时 4 页（各 25%），仅绘制高亮段、不画底轨。
 * 3. **手势**（三者共存，并按下即分流，避免相互误触）：
 *    - 轻点：回调 [onClick]（展开菜单）；
 *    - 长按：回调 [onLongClick]（切换隐私模式）；
 *    - 按压后**向下**拖动超过 touchSlop：胶囊下方弹出跟手的 fab，拖动到 fab 上会震动提示，
 *      松手落在 fab 上时回调 [onScrollToTop]；其余情况（未到 fab 即松手 / 到达后移开）不产生效果。
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
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onScrollToTop: () -> Unit,
    onBarHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val customValue = remember(reloadTick) {
        Preferences.customSloganValue.get()
    }
    val themeColor = currentThemeColor()

    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current

    // 回调可能随重组变化，用 rememberUpdatedState 保证手势 / 尺寸回调里读到的始终是最新引用
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentOnScrollToTop by rememberUpdatedState(onScrollToTop)
    val currentOnBarHeightChanged by rememberUpdatedState(onBarHeightChanged)

    val fabSizePx = with(density) { ScrollTopFabSize.toPx() }
    val fabGapPx = with(density) { ScrollTopFabGap.toPx() }
    val strokeWidthPx = with(density) { TabOutlineStrokeWidth.toPx() }

    // 是否处于「下滑出 fab」状态：驱动 fab 的「缩放 + 透明度」过渡
    var fabVisible by remember { mutableStateOf(false) }
    // 手势互斥标记：下滑出 fab 期间抑制 combinedClickable 的误点击（对事件分发顺序的兜底）
    val dragState = remember { SloganGestureState() }

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
                .shadow(elevation = SloganBarElevation, shape = SloganBarShape)
                .clip(SloganBarShape)
                .background(themeColor.windowBackground)
                .combinedClickable(
                    onClick = {
                        // 下滑出 fab 的同一手势不应再被判为点击，避免误开菜单
                        if (!dragState.isDraggingDown) {
                            currentOnClick()
                        }
                    },
                    onLongClick = { currentOnLongClick() }
                )
                .pointerInput(fabSizePx, fabGapPx) {
                    detectSloganGesture(
                        fabSizePx = fabSizePx,
                        fabGapPx = fabGapPx,
                        onScrollToTop = { currentOnScrollToTop() },
                        onDragStateChanged = { dragging ->
                            dragState.isDraggingDown = dragging
                            fabVisible = dragging
                        },
                        onHapticFeedback = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                        }
                    )
                }
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

        // fab：绝对定位到胶囊正下方，不参与 Box 尺寸（否则会把菜单锚点撑高）
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .belowCenterOverlay(topOffset = ScrollTopFabGap)
        ) {
            ScrollTopFab(
                visible = fabVisible,
                containerColor = themeColor.buttonBackground,
                contentColor = themeColor.buttonText
            )
        }
    }
}

/**
 * 胶囊下方的滚回顶部 fab。单独抽成非 [BoxScope] 的 Composable，
 * 以便使用顶层（非 BoxScope 扩展）的 [AnimatedVisibility] 重载。
 */
@Composable
private fun ScrollTopFab(
    visible: Boolean,
    containerColor: Color,
    contentColor: Color
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.5F),
        exit = fadeOut() + scaleOut(targetScale = 0.5F)
    ) {
        FloatingActionButton(
            onClick = {},
            modifier = Modifier.size(ScrollTopFabSize),
            shape = CircleShape,
            containerColor = containerColor,
            contentColor = contentColor
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(ScrollTopFabIconSize)
            )
        }
    }
}

/**
 * 把自身（fab）测量为 0 尺寸、并相对父节点原点**水平居中、垂直下移** [topOffset] 地摆放。
 *
 * 这样 fab 可以叠在胶囊下方绘制，却不会撑大父容器，保证外部菜单锚点始终贴合胶囊本体。
 */
private fun Modifier.belowCenterOverlay(topOffset: Dp): Modifier {
    return this.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(width = 0, height = 0) {
            placeable.place(
                x = -placeable.width / 2,
                y = topOffset.roundToPx()
            )
        }
    }
}

/** 保存「下滑出 fab」手势的互斥标记（跨重组稳定，供手势与点击回调共享）。 */
private class SloganGestureState {
    var isDraggingDown: Boolean = false
}

/** 手势分流结果。 */
private enum class SloganGestureDecision {
    /** 未产生位移即松手：交给 combinedClickable 处理轻点。 */
    Up,

    /** 按住超过长按阈值：交给 combinedClickable 处理长按（切换隐私）。 */
    LongPress,

    /** 向下拖动超过 touchSlop：进入 fab 模式。 */
    DragDown,

    /** 反向 / 横向拖动超过 touchSlop：本次手势作废。 */
    Cancel
}

/**
 * 在不打断 combinedClickable（轻点 / 长按）的前提下，额外识别「向下拖动出 fab」：
 *
 * - 在 [AwaitPointerEventScope] 中先等待一次「轻点 / 长按 / 拖动」的分流结果；
 * - 一旦判定为拖动，即消费事件（阻止 combinedClickable 误判为点击），随后跟手追踪，
 *   命中 fab 时震动，抬手落在 fab 上则回调回到顶部。
 */
private suspend fun PointerInputScope.detectSloganGesture(
    fabSizePx: Float,
    fabGapPx: Float,
    onScrollToTop: () -> Unit,
    onDragStateChanged: (Boolean) -> Unit,
    onHapticFeedback: () -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val decision = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            awaitDecision(down, viewConfiguration.touchSlop)
        } ?: SloganGestureDecision.LongPress

        when (decision) {
            SloganGestureDecision.Up -> {
                // 轻点：不消费事件，交给 combinedClickable 触发 onClick
            }

            SloganGestureDecision.LongPress,
            SloganGestureDecision.Cancel -> {
                // 长按已由 combinedClickable 处理；这里仅等待抬手结束本次手势
                awaitPointerEnd(down.id)
            }

            SloganGestureDecision.DragDown -> {
                onDragStateChanged(true)
                var hitFab = false
                try {
                    val radius = fabSizePx / 2F
                    val centerX = size.width / 2F
                    val centerY = size.height + fabGapPx + radius
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) {
                            if (hitFab) {
                                onScrollToTop()
                            }
                            break
                        }
                        val offsetX = change.position.x - centerX
                        val offsetY = change.position.y - centerY
                        val inside = offsetX * offsetX + offsetY * offsetY <= radius * radius
                        if (inside && !hitFab) {
                            onHapticFeedback()
                        }
                        hitFab = inside
                    }
                } finally {
                    onDragStateChanged(false)
                }
            }
        }
    }
}

/**
 * 等待「轻点 / 长按 / 向下拖动 / 取消」的首次判定。
 *
 * 该函数置于 [withTimeoutOrNull] 中调用：超时即视为长按，由调用方兜底为
 * [SloganGestureDecision.LongPress]。
 */
private suspend fun AwaitPointerEventScope.awaitDecision(
    down: PointerInputChange,
    touchSlop: Float
): SloganGestureDecision {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id }
        if (change == null || !change.pressed) {
            return SloganGestureDecision.Up
        }
        val offsetX = change.position.x - down.position.x
        val offsetY = change.position.y - down.position.y
        if (offsetY > touchSlop) {
            change.consume()
            return SloganGestureDecision.DragDown
        }
        if (offsetY < -touchSlop || abs(offsetX) > touchSlop) {
            change.consume()
            return SloganGestureDecision.Cancel
        }
    }
}

/** 等待指定指针抬手（不消费事件，避免影响 combinedClickable 的长按判定）。 */
private suspend fun AwaitPointerEventScope.awaitPointerEnd(pointerId: PointerId) {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == pointerId } ?: return
        if (!change.pressed) {
            return
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
