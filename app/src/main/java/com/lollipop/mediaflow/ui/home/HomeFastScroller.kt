package com.lollipop.mediaflow.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lollipop.mediaflow.ui.home.gesture.HomeGestureTarget
import com.lollipop.mediaflow.ui.home.gesture.HomeGestureVerdict
import com.lollipop.mediaflow.ui.theme.currentThemeColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 瀑布流的快速定位拖拽条（贴右侧）。
 *
 * ## 职责拆分
 *
 * 本组件**只负责绘制**（[HomeFastScroller]），手势识别交给首页专用容器
 * [`HomeGestureHost`][com.lollipop.mediaflow.ui.home.gesture.HomeGestureHost] 分发：
 * [HomeFastScrollerState] 实现 [`HomeGestureTarget`][com.lollipop.mediaflow.ui.home.gesture.HomeGestureTarget]，
 * 登记自己的命中区（右侧 [FastScrollerTouchWidth] 宽的一条竖带）与优先级。
 *
 * 为什么不能自己挂 `pointerInput`：Compose 的命中测试只把事件交给最上层带指针输入的兄弟节点
 * （`sharePointerInputWithSiblings()` 默认 `false`），因此叠在列表之上的本组件会**整体遮住**
 * 下层兄弟——标题行右侧的排序 / 文件夹按钮因此点不到，且「不消费事件」并不能让点击穿透
 * （事件根本没进下层兄弟的命中路径）。挂到根容器后：祖先本就在子节点的命中路径上，
 * 单击照常落到按钮，只有在判定为拖动后容器才消费事件。
 *
 * ## 几何：与「列表可视区」对齐
 *
 * 本组件所在容器是全屏的（含状态栏 / 导航栏区域），但列表内容被 `contentPadding` 收窄。
 * 因此轨道的上下边界与右侧位置都**由页面注入**（[trackTopInset] / [trackBottomInset] / [rightInset]），
 * 绘制、命中区、拖动换算三处共用同一份数值，天然一致：
 * ```
 * 命中区 = [容器右 - rightInset - touchWidth, 容器右 - rightInset] × [trackTop, 容器高 - trackBottom]
 * ```
 * 好处：滑块不会钻进系统栏 / 手势条区域；靠近边界也能正常起手拖动。
 *
 * ## 误触治理
 *
 * - **热区收窄**到 [FastScrollerTouchWidth]；
 * - **方向仲裁**（见 [HomeFastScrollerState.evaluate]）：纵向先超过 `touchSlop` 才接管，
 *   横向先超过则放弃，避免把 Pager 的横向翻页抢走；
 * - **长按激活**：无位移地按住超过长按阈值时震动提示（[HomeFastScrollerState.onLongPressArm]），
 *   之后任一方向拖动即可接管。
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
 * 于是真正需要的尺寸只有两个，且都拿得到：**轨道区高度**（绘制层回报）与
 * **条目总数**（由页面按数据源传入）。滑块长度取固定值，不做比例。
 *
 * ## 已知取舍
 *
 * 索引比例 ≠ 视觉高度比例（卡片有高有矮）：拖到 50% 表示「约一半条目」，而非
 * 「内容高度的一半」。作为快速穿越足够；若真要精确比例，只能按平均高度估算，收益有限。
 */

/** 未在拖动。 */
private const val NoDragIndex = -1

/**
 * 拖拽条状态：既驱动绘制（滑块位置 / 气泡），也是容器分发的 [`HomeGestureTarget`]。
 *
 * 通过 [rememberHomeFastScrollerState] 创建。
 */
@Stable
class HomeFastScrollerState internal constructor(
    internal val gridState: LazyStaggeredGridState,
    private val scope: CoroutineScope,
    private val density: Density,
    private val headerItemCount: Int,
    private val minItemCount: Int,
    /** 接管时的触觉反馈：让用户立刻知道「抓住了」（没有视觉反馈时也能确认已触发）。 */
    private val haptics: HapticFeedback,
    /** 轨道上留白（相对容器，跟随列表可视区顶部）。 */
    internal val trackTopInset: Dp,
    /** 轨道下留白（相对容器，跟随列表可视区底部）。 */
    internal val trackBottomInset: Dp,
    /** 轨道距屏幕右边缘的留白（跟随右侧安全区，替代旧的固定边距）。 */
    internal val rightInset: Dp,
) : HomeGestureTarget {

    /**
     * 列表条目总数（含头部条目）。
     *
     * 由调用方用**数据源**给出（如 `媒体数 + headerItemCount`），不要取
     * `layoutInfo.totalItemsCount`：后者在尚未测量 / 数据刚变化的瞬间可能不完整，
     * 会让气泡里的序号与总数退化成一屏内的范围。
     *
     * ⚠️ 必须是**可观察状态**（而非普通字段）：[isActive] 由它派生，决定整条拖拽条是否绘制。
     * 若用普通字段，数据从「未加载」变为「已加载」时不会触发重组，组件会停留在
     * 「首次组合即 return」的状态——表现为「手势可用（[bounds] 是实时求值），但绘制整块不见」。
     */
    var totalItemCount by mutableIntStateOf(0)

    /** 热区宽度（px）。 */
    private val touchWidthPx: Float = with(density) { FastScrollerTouchWidth.toPx() }

    /** 滑块高度（px，固定值，不做比例）。 */
    private val sliderHeightPx: Float = with(density) { FastScrollerSliderHeight.toPx() }

    /**
     * 轨道上下留白（px）：与绘制层用的是**同一份 Dp 值换算**，因此拖动换算与视觉天然一致。
     *
     * 刻意不从绘制层回读几何：`onGloballyPositioned { boundsInParent() }` 在本组件的修饰符链上
     * 实测拿到的是零矩形（真机日志 `track=Rect.fromLTRB(0,0,0,0)`），会导致拖动换算每帧早退。
     */
    private val trackTopInsetPx: Float = with(density) { trackTopInset.toPx() }
    private val trackBottomInsetPx: Float = with(density) { trackBottomInset.toPx() }

    /** 右侧留白（px）：命中区右边界以此内缩，避开系统边缘手势区。 */
    private val rightInsetPx: Float = with(density) { rightInset.toPx() }

    /** 手势起点时的容器高度（px），由 [bounds] 记录：留白兜底时要用它。 */
    private var containerHeightPx: Float = 0F

    /** 绘制层实测的轨道高度（px）：几何的**第一来源**，由 [onTrackMeasured] 在布局后写入。 */
    private var measuredTrackHeightPx by mutableFloatStateOf(0F)

    /** 是否正在拖动（拖动中滑块加粗并显示气泡）。 */
    var isDragging by mutableStateOf(false)
        internal set

    /** 是否已「长按激活」：此后任一方向拖动即接管，用于回馈长按提示。 */
    private var armed by mutableStateOf(false)

    /** 拖动中的目标索引（[NoDragIndex] = 未拖动）。 */
    var dragIndex by mutableIntStateOf(NoDragIndex)
        internal set

    /** 拖动期间的滚动任务：每次移动都取消上一个，避免任务堆积。 */
    private var scrollJob: Job? = null

    /** 媒体条目数（扣除头部条目），用于气泡文案。 */
    val mediaTotal: Int
        get() = totalItemCount - headerItemCount

    /** 是否达到显示 / 生效阈值（条目太少时直接滑动更自然）。 */
    val isActive: Boolean
        get() = mediaTotal >= minItemCount

    /**
     * 当前位置比例。
     *
     * 拖动中直接跟到**目标索引**（与列表的跳跃式定位一致，不做逐帧连续换算）；非拖动时用
     * 列表首条目索引。
     */
    val progress: Float
        get() {
            if (totalItemCount <= 1) {
                return 0F
            }
            val value = if (isDragging && dragIndex != NoDragIndex) {
                dragIndex / (totalItemCount - 1F)
            } else {
                gridState.firstVisibleItemIndex / (totalItemCount - 1F)
            }
            return value.coerceIn(0F, 1F)
        }

    /**
     * 轨道高度（px，不含上下留白）。
     *
     * 以**绘制层实测值**为准（与视觉是同一个数）；未测量时用容器高度扣留白兜底。
     * 单一来源的好处：拖动换算与滑块定位不可能各算一套而错位。
     */
    fun trackHeightPx(): Float {
        if (measuredTrackHeightPx > 0F) {
            return measuredTrackHeightPx
        }
        return (containerHeightPx - trackTopInsetPx - trackBottomInsetPx).coerceAtLeast(0F)
    }

    /** 绘制层回报轨道高度（布局后调用）。 */
    internal fun onTrackMeasured(heightPx: Float) {
        measuredTrackHeightPx = heightPx
    }

    /** 滑块高度（px，供绘制层复用同一份换算）。 */
    fun sliderHeight(): Float = sliderHeightPx

    /** 气泡里的「第 N 项」（媒体条目维度，扣除头部条目）。 */
    fun bubbleIndex(): Int {
        return (dragIndex - headerItemCount).coerceAtLeast(0) + 1
    }

    // ---- HomeGestureTarget ----

    /**
     * 命中区：紧贴右侧安全区的竖带，纵向只覆盖**列表可视区**（跟随上下 insets）。
     *
     * - 右边界 = `容器宽 - rightInset`（避开系统边缘手势区）；
     * - 左边界 = 右边界 - [FastScrollerTouchWidth]（收窄后的热区）；
     * - 上下边界 = 轨道上下留白之间，避免在系统栏 / 手势条区域起手。
     *
     * 顺带记录容器高度：手势起点是本组件唯一能拿到容器尺寸的时机，[dragTo] 的换算就靠它。
     */
    override fun bounds(container: IntSize): Rect? {
        if (!isActive || touchWidthPx <= 0F || container.width <= 0 || container.height <= 0) {
            return null
        }
        containerHeightPx = container.height.toFloat()
        val right = (container.width - rightInsetPx).coerceAtLeast(0F)
        val left = (right - touchWidthPx).coerceAtLeast(0F)
        val top = trackTopInsetPx
        val bottom = (container.height - trackBottomInsetPx).coerceAtLeast(top)
        return Rect(left, top, right, bottom)
    }

    /**
     * 高于子节点（按钮 / 卡片视作优先级 `0`）：因此即使用户的手指落在卡片上，
     * 只要随后纵向拖动，就应归拖拽条而不是列表滚动。
     */
    override val priority: Int get() = 1

    /**
     * 每帧判定本次手势归属。
     *
     * - **未激活**：只有「纵向位移先超过 `touchSlop`，且纵向分量大于横向」才接管；
     *   若**横向先超过**阈值（例如 Pager 翻页），返回 [HomeGestureVerdict.Abandon]，
     *   本次手势内不再询问，事件交还翻页。
     * - **已长按激活**：任一方向超过阈值即接管。
     */
    override fun evaluate(down: Offset, current: Offset, slop: Float): HomeGestureVerdict {
        val dx = abs(current.x - down.x)
        val dy = abs(current.y - down.y)
        if (armed) {
            return if (dx > slop || dy > slop) {
                HomeGestureVerdict.Capture
            } else {
                HomeGestureVerdict.Pending
            }
        }
        return when {
            dy > slop && dy > dx -> HomeGestureVerdict.Capture
            dx > slop && dx >= dy -> HomeGestureVerdict.Abandon
            else -> HomeGestureVerdict.Pending
        }
    }

    /**
     * 长按激活：无位移地按住超过长按阈值时调用。
     *
     * 震动一下提示「已进入拖拽模式」，此后任一方向拖动都会接管。
     */
    override fun onLongPressArm() {
        if (armed) {
            return
        }
        armed = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    override fun onCapture(down: Offset, current: Offset) {
        isDragging = true
        // 长按已经震过一次，拖动接管时不再重复震；直接拖动则震一次作为「抓住」的确认。
        if (!armed) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        armed = false
    }

    override fun onMove(current: Offset) {
        dragTo(current.y)
    }

    override fun onRelease() {
        isDragging = false
        dragIndex = NoDragIndex
        armed = false
    }

    /** 手指位置（容器坐标）→ 轨道进度（0..1）：滑块中心跟随手指。 */
    private fun fractionOf(containerY: Float): Float {
        val trackHeight = trackHeightPx()
        if (trackHeight <= 0F) {
            return 0F
        }
        val local = (containerY - trackTopInsetPx).coerceIn(0F, trackHeight)
        val movable = (trackHeight - sliderHeightPx).coerceAtLeast(1F)
        return ((local - sliderHeightPx / 2F) / movable).coerceIn(0F, 1F)
    }

    /** 把容器坐标系里的手指位置换算成目标索引并滚动过去（无动画，拖动期间直接跳）。 */
    internal fun dragTo(containerY: Float) {
        val trackHeight = trackHeightPx()
        if (totalItemCount <= 0 || trackHeight <= 0F) {
            return
        }
        val ratio = fractionOf(containerY)
        val index = (ratio * (totalItemCount - 1)).roundToInt().coerceIn(0, totalItemCount - 1)
        dragIndex = index
        scrollJob?.cancel()
        scrollJob = scope.launch { gridState.scrollToItem(index) }
    }
}

/**
 * 记住拖拽条状态。
 *
 * @param totalItemCount 列表条目总数（含头部条目），由页面按数据源给出。
 * @param headerItemCount 媒体条目之前的头部条目数（气泡把索引换算成「第 N 项」时扣除）。
 * @param minItemCount 媒体条目数少于该值时不显示、也不参与手势分发。
 * @param trackTopInset 轨道上留白（应跟随列表可视区顶部，例如 `contentPadding` 的 top）。
 * @param trackBottomInset 轨道下留白（应跟随列表可视区底部，例如 `contentPadding` 的 bottom）。
 * @param rightInset 轨道距屏幕右边缘的留白（跟随右侧安全区）。
 */
@Composable
fun rememberHomeFastScrollerState(
    gridState: LazyStaggeredGridState,
    totalItemCount: Int,
    headerItemCount: Int = 0,
    minItemCount: Int = FastScrollerMinItemCount,
    trackTopInset: Dp = 0.dp,
    trackBottomInset: Dp = 0.dp,
    rightInset: Dp = 0.dp,
): HomeFastScrollerState {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val state = remember(
        gridState, scope, density, headerItemCount, minItemCount,
        trackTopInset, trackBottomInset, rightInset
    ) {
        HomeFastScrollerState(
            gridState = gridState,
            scope = scope,
            density = density,
            headerItemCount = headerItemCount,
            minItemCount = minItemCount,
            haptics = haptics,
            trackTopInset = trackTopInset,
            trackBottomInset = trackBottomInset,
            rightInset = rightInset
        )
    }
    // 条目总数随数据变化（分页 / 刷新 / 归档）。写成可观察状态：它派生出的 [isActive]
    // 决定拖拽条是否绘制，必须能在数据就绪后触发一次重组，否则组件会一直停留在未渲染状态。
    state.totalItemCount = totalItemCount
    return state
}

/**
 * 拖拽条绘制层（**不含手势**）。
 *
 * 命中与拖动由首页容器 [`HomeGestureHost`][com.lollipop.mediaflow.ui.home.gesture.HomeGestureHost]
 * 按 [HomeFastScrollerState] 登记的命中区分发；本组件只画**滑块**（不要背景轨道）与拖动中的序号气泡，
 * 并把实测轨道高度回报给状态。
 */
@Composable
fun HomeFastScroller(
    state: HomeFastScrollerState,
    modifier: Modifier = Modifier
) {
    if (!state.isActive) {
        return
    }
    val themeColor = currentThemeColor()
    val density = LocalDensity.current
    val sliderHeightPx = state.sliderHeight()

    /** 气泡尺寸（px）：用于把它放到拖拽条左侧并垂直对齐滑块。 */
    var bubbleSize by remember { mutableStateOf(IntSize.Zero) }

    // 空闲淡出：拖动 / 滚动中保持可见，停下并等待若干毫秒后淡出
    val alpha = remember { Animatable(0F) }
    LaunchedEffect(state.isDragging, state.gridState.isScrollInProgress, state.totalItemCount) {
        if (state.isDragging || state.gridState.isScrollInProgress) {
            alpha.snapTo(1F)
            return@LaunchedEffect
        }
        // 空闲：先亮一会儿提示「这里能拖」，再淡出
        alpha.snapTo(1F)
        delay(FastScrollerIdleVisibleMs)
        alpha.animateTo(0F, tween(FastScrollerFadeMs))
    }

    // 当前位置（拖动中跟手）与滑块顶端：几何取状态里的**单一来源**
    val trackHeightPx = state.trackHeightPx()
    val sliderTop = state.progress * (trackHeightPx - sliderHeightPx).coerceAtLeast(0F)

    Box(
        modifier = modifier
            // 整体左移 rightInset：视觉与命中区共用同一份右留白（单一来源）
            .offset(x = -state.rightInset)
            .width(FastScrollerTouchWidth)
            .fillMaxHeight()
            // 上下留白只影响绘制：命中区由状态按轨道区间登记，取值与这里一致
            .padding(top = state.trackTopInset, bottom = state.trackBottomInset)
            .graphicsLayer { this.alpha = alpha.value }
            // 不挂 pointerInput（命中由根容器分发）；这里只回报轨道高度（几何的唯一来源）
            .onSizeChanged { state.onTrackMeasured(it.height.toFloat()) }
    ) {
        // 只画滑块：不要背景轨道（一条线既不好看也喧宾夺主）。
        // 颜色取主题「滑块色」（半透明青绿），拖动时略实一点作为「已抓住」的反馈。
        val isDragging = state.isDragging
        val thumbColor = themeColor.buttonSlider.copy(
            alpha = if (isDragging) SliderDraggingAlpha else SliderAlpha
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val baseWidthPx = FastScrollerSliderWidth.toPx()
            val thumbWidthPx = baseWidthPx +
                if (isDragging) FastScrollerSliderExtraWidth.toPx() else 0F
            // 右边缘对齐热区右边缘、向**左**生长：加宽时不会越出 Canvas 而在屏幕边缘被裁掉。
            // （滑块距屏幕右侧的留白已由外层 `offset` 处理，这里不再额外内缩。）
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(size.width - thumbWidthPx, sliderTop),
                size = Size(thumbWidthPx, sliderHeightPx),
                cornerRadius = CornerRadius(thumbWidthPx / 2F),
            )
        }

        // 拖动中：气泡显示「第 N 项 / 共 M 项」（媒体条目维度，扣除头部条目）
        if (isDragging && state.dragIndex != NoDragIndex) {
            val mediaIndex = state.bubbleIndex()
            val bubbleShape = remember { RoundedCornerShape(BubbleCornerRadius) }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    // ⚠️ 本层只有热区宽度，气泡若继续受父约束会被压到几十 dp，
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
                    text = "$mediaIndex / ${state.mediaTotal}",
                    style = plainTextStyle(16.sp),
                    color = themeColor.buttonText,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 触摸区宽度：缩小到 32dp。
 *
 * 之前的 56dp 会大面积压住右侧列卡片，导致「在卡片上正常纵向滚动」被判定成拖拽而误触；
 * 收窄后覆盖面积明显下降，配合方向仲裁（横向优先放弃）基本可消除误触。
 */
private val FastScrollerTouchWidth = 32.dp

/** 轨道在「列表可视区」之上的额外上留白：不贴安全区顶部，避免在状态栏 / 手势区起手。 */
internal val FastScrollerTrackExtraTopInset = 8.dp

/** 轨道在「列表可视区」之下的额外下留白：不贴安全区底部（避开底部 Tab 胶囊与手势条）。 */
internal val FastScrollerTrackExtraBottomInset = 16.dp

/** 滑块宽度：做成圆润胶囊而不是细线（拖动时再加宽 [FastScrollerSliderExtraWidth]）。 */
private val FastScrollerSliderWidth = 8.dp

/** 滑块高度：固定长度（内容总高度不可得，故不做比例长度）。 */
private val FastScrollerSliderHeight = 36.dp

/** 拖动时滑块的额外宽度（「已抓住」的反馈）。 */
private val FastScrollerSliderExtraWidth = 4.dp

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

/** 滑块静止透明度（拖动时用 [SliderDraggingAlpha]）。 */
private const val SliderAlpha = 0.6F

/** 滑块拖动中透明度：比静止略实一点，作为「已抓住」的反馈。 */
private const val SliderDraggingAlpha = 0.9F

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
