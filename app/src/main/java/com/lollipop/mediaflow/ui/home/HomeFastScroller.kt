package com.lollipop.mediaflow.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lollipop.mediaflow.ui.theme.currentThemeColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 瀑布流的快速定位拖拽条（贴右侧）。
 *
 * ## 命中与手势
 *
 * 热区刻意做得比视觉条宽（[FastScrollerTouchWidth]），但**不消费单纯的按下**：
 * 只有纵向位移超过 `touchSlop` 才判定为拖拽并开始消费事件，因此
 * ① 侧边上下滑动必定触发快速定位（不会被列表抢走）；
 * ② 只是点一下时事件照常穿透到列表，卡片点击不受影响。
 *
 * ## 为什么用「索引比例」而不是「内容高度比例」
 *
 * `LazyVerticalStaggeredGrid` 在测量前并不知道内容总高度（惰性布局 + 卡片高矮不一 + 分页还在增长），
 * 所以「按可见比例画滑块长度 / 按像素比例算位置」那条路不可用。
 * 这里改用**条目维度**：
 * ```
 * 位置 = firstVisibleItemIndex / (totalItemsCount - 1)
 * 拖动 = 滑块位移 → 目标索引 → scrollToItem(index)
 * ```
 * 于是真正需要的尺寸只有两个，且都拿得到：**本组件自身高度**（`onSizeChanged`）与
 * **条目总数**（`layoutInfo.totalItemsCount`）。滑块长度取固定值，不做比例。
 *
 * ## 已知取舍
 *
 * 索引比例 ≠ 视觉高度比例（卡片有高有矮）：拖到 50% 表示「约一半条目」，而非
 * 「内容高度的一半」。作为快速穿越足够；若真要精确比例，只能按平均高度估算，收益有限。
 *
 * ## 交互
 *
 * - 触摸区固定宽度（[FastScrollerTouchWidth]），按下即定位、拖动持续定位（过程中消费事件，
 *   因此不会与列表滚动 / 下拉刷新抢手势）；
 * - 拖动中显示「第 N 项 / 共 M 项」气泡（[headerItemCount] 用于扣除头部条目）；
 * - 空闲 [FastScrollerIdleVisibleMs] 后自动淡出，滚动或拖动时重新显现；
 * - 条目数少于 [FastScrollerMinItemCount] 时整体不显示（内容不多时直接滑更自然）。
 */
@Composable
fun HomeFastScroller(
    state: LazyStaggeredGridState,
    /**
     * 列表条目总数（含头部条目）。
     *
     * 由调用方用**数据源**给出（如 `媒体数 + headerItemCount`），不要取
     * `layoutInfo.totalItemsCount`：后者在尚未测量 / 数据刚变化的瞬间可能不完整，
     * 会让气泡里的序号与总数退化成一屏内的范围。传 0 表示回退到 `layoutInfo`。
     */
    totalItemCount: Int,
    modifier: Modifier = Modifier,
    /** 媒体条目之前的头部条目数：气泡把索引换算成「第 N 项」时扣除它。 */
    headerItemCount: Int = 0,
    /** 媒体条目数少于该值时不显示。 */
    minItemCount: Int = FastScrollerMinItemCount,
) {
    val layoutInfo = state.layoutInfo
    // 优先用调用方给的准确值；`layoutInfo` 只作兜底
    val totalCount = if (totalItemCount > 0) totalItemCount else layoutInfo.totalItemsCount
    val mediaTotal = totalCount - headerItemCount
    if (mediaTotal < minItemCount) {
        return
    }

    val themeColor = currentThemeColor()
    val scope = rememberCoroutineScope()

    /** 本组件高度（px）：滑块可移动范围由它决定。 */
    var trackHeightPx by remember { mutableFloatStateOf(0F) }

    /** 气泡尺寸（px）：用于把它放到拖拽条左侧并垂直对齐滑块。 */
    var bubbleSize by remember { mutableStateOf(IntSize.Zero) }

    /** 是否正在拖动（拖动中滑块加粗并显示气泡）。 */
    var isDragging by remember { mutableStateOf(false) }

    /** 拖动中的目标索引（-1 = 未拖动）。 */
    var dragIndex by remember { mutableIntStateOf(NoDragIndex) }

    /** 拖动期间的滚动任务：每次移动都取消上一个，避免任务堆积。 */
    var scrollJob by remember { mutableStateOf<Job?>(null) }

    val density = LocalDensity.current
    val sliderHeightPx = with(density) { FastScrollerSliderHeight.toPx() }

    // 当前位置比例：优先用拖动目标（跟手），否则用列表首条目索引
    val listProgress = if (totalCount > 1) {
        state.firstVisibleItemIndex / (totalCount - 1F)
    } else {
        0F
    }
    val progress = if (isDragging && dragIndex != NoDragIndex && totalCount > 1) {
        dragIndex / (totalCount - 1F)
    } else {
        listProgress
    }.coerceIn(0F, 1F)

    /** 滑块顶端（px）。 */
    val sliderTop = progress * (trackHeightPx - sliderHeightPx).coerceAtLeast(0F)

    /** 把手指位置换算成目标索引并滚动过去（无动画，拖动期间直接跳）。 */
    fun dragTo(y: Float) {
        val movable = (trackHeightPx - sliderHeightPx).coerceAtLeast(1F)
        // 让滑块中心跟随手指
        val ratio = ((y - sliderHeightPx / 2F) / movable).coerceIn(0F, 1F)
        val index = (ratio * (totalCount - 1)).roundToInt().coerceIn(0, totalCount - 1)
        dragIndex = index
        scrollJob?.cancel()
        scrollJob = scope.launch { state.scrollToItem(index) }
    }

    // 空闲淡出：拖动 / 滚动中保持可见，停下并等待若干毫秒后淡出
    val alpha = remember { Animatable(0F) }
    LaunchedEffect(isDragging, state.isScrollInProgress, totalCount) {
        if (isDragging || state.isScrollInProgress) {
            alpha.snapTo(1F)
            return@LaunchedEffect
        }
        // 空闲：先亮一会儿提示「这里能拖」，再淡出
        alpha.snapTo(1F)
        delay(FastScrollerIdleVisibleMs)
        alpha.animateTo(0F, tween(FastScrollerFadeMs))
    }

    Box(
        modifier = modifier
            .width(FastScrollerTouchWidth)
            .fillMaxHeight()
            .graphicsLayer { this.alpha = alpha.value }
            .onSizeChanged { trackHeightPx = it.height.toFloat() }
            // ⚠️ key 必须带上尺寸：首帧 `trackHeightPx` 还是 0，本协程会立刻返回；
            // 若 key 只有 `totalCount`，尺寸就绪后不会重启，手势将永久失效。
            .pointerInput(totalCount, trackHeightPx) {
                if (trackHeightPx <= 0F) {
                    return@pointerInput
                }
                val slop = viewConfiguration.touchSlop
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startY = down.position.y
                        var dragging = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                break
                            }
                            if (!dragging && abs(change.position.y - startY) > slop) {
                                // 超过 touchSlop 才判定为拖拽：单纯点击不消费事件，
                                // 卡片的点击因此仍然正常穿透到下层。
                                dragging = true
                                isDragging = true
                            }
                            if (dragging) {
                                dragTo(change.position.y)
                                change.consume()
                            }
                        }
                        if (dragging) {
                            isDragging = false
                            dragIndex = NoDragIndex
                        }
                    }
                }
            }
    ) {
        val trackColor = themeColor.buttonText.copy(alpha = TrackAlpha)
        val sliderColor = themeColor.buttonText.copy(alpha = if (isDragging) 1F else SliderAlpha)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackWidthPx = FastScrollerTrackWidth.toPx()
            val rightMarginPx = FastScrollerRightMargin.toPx()
            val centerX = size.width - rightMarginPx - trackWidthPx / 2F
            // 轨道
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(centerX - trackWidthPx / 2F, 0F),
                size = Size(trackWidthPx, size.height),
                cornerRadius = CornerRadius(trackWidthPx / 2F),
            )
            // 滑块：拖动时略宽，表示「已抓住」
            val extraWidthPx = if (isDragging) {
                FastScrollerSliderExtraWidth.toPx()
            } else {
                0F
            }
            val sliderWidthPx = trackWidthPx + extraWidthPx
            drawRoundRect(
                color = sliderColor,
                topLeft = Offset(centerX - sliderWidthPx / 2F, sliderTop),
                size = Size(sliderWidthPx, sliderHeightPx),
                cornerRadius = CornerRadius(sliderWidthPx / 2F),
            )
        }

        // 拖动中：气泡显示「第 N 项 / 共 M 项」（媒体条目维度，扣除头部条目）
        if (isDragging && dragIndex != NoDragIndex) {
            val mediaIndex = (dragIndex - headerItemCount).coerceAtLeast(0) + 1
            val bubbleShape = remember { RoundedCornerShape(BubbleCornerRadius) }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    // ⚠️ 本层只有热区宽（56dp），气泡若继续受父约束会被压到几十 dp，
                    // 文本遭压缩后会显示成「3 /」这类残缺内容（看着像凭空多了个斜杠）。
                    // 解除宽度约束、按内容测量，再用 offset 把它画到热区左侧。
                    .wrapContentWidth(unbounded = true)
                    .onSizeChanged { bubbleSize = it }
                    .offset {
                        IntOffset(
                            x = -(bubbleSize.width + with(density) { FastScrollerBubbleGap.toPx() }.toInt()),
                            y = (sliderTop + sliderHeightPx / 2F - bubbleSize.height / 2F).roundToInt(),
                        )
                    }
                    // 阴影 + 细描边：与列表内容拉开层次
                    .shadow(elevation = BubbleElevation, shape = bubbleShape)
                    .background(color = themeColor.buttonBackground, shape = bubbleShape)
                    .border(
                        width = 1.dp,
                        color = themeColor.buttonText.copy(alpha = BubbleBorderAlpha),
                        shape = bubbleShape,
                    )
                    .padding(
                        horizontal = BubblePaddingHorizontal,
                        vertical = BubblePaddingVertical,
                    )
            ) {
                Text(
                    text = "$mediaIndex / $mediaTotal",
                    style = plainTextStyle(16.sp),
                    color = themeColor.buttonText,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 未在拖动。 */
private const val NoDragIndex = -1

/**
 * 触摸区宽度：视觉条只有几 dp，但热区要够宽才好命中。
 *
 * 加宽不会抢走点击——手势只在纵向位移超过 `touchSlop` 后才消费事件（见文件头说明）。
 */
private val FastScrollerTouchWidth = 56.dp

/** 轨道宽度。 */
private val FastScrollerTrackWidth = 4.dp

/** 滑块高度：固定长度（内容总高度不可得，故不做比例长度）。 */
private val FastScrollerSliderHeight = 48.dp

/** 拖动时滑块相对轨道的额外宽度。 */
private val FastScrollerSliderExtraWidth = 4.dp

/** 轨道距屏幕右边缘的留白。 */
private val FastScrollerRightMargin = 6.dp

/** 气泡与拖拽条的间距（留大一些，避免被拇指遮住）。 */
private val FastScrollerBubbleGap = 24.dp

/** 气泡圆角。 */
private val BubbleCornerRadius = 18.dp

/** 气泡左右内边距。 */
private val BubblePaddingHorizontal = 18.dp

/** 气泡上下内边距。 */
private val BubblePaddingVertical = 10.dp

/** 气泡阴影高度：与背景画面分离，避免糊在列表上。 */
private val BubbleElevation = 8.dp

/** 气泡描边透明度（配合阴影强化边界）。 */
private const val BubbleBorderAlpha = 0.15F

/** 轨道静止透明度。 */
private const val TrackAlpha = 0.18F

/** 滑块静止透明度（拖动时用 1F）。 */
private const val SliderAlpha = 0.65F

/** 空闲多久后淡出（ms）。 */
private const val FastScrollerIdleVisibleMs = 2000L

/** 淡出时长（ms）。 */
private const val FastScrollerFadeMs = 300

/**
 * 媒体条目数少于该值时不显示拖拽条。
 *
 * 取 24：按每屏 4~6 条估算，24 条约相当于 4~6 屏，再少就直接滑动更快。
 * 调整手感改这里即可。
 */
private const val FastScrollerMinItemCount = 24
