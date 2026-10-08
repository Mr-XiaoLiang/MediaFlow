package com.lollipop.mediaflow.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.ui.HomePage
import com.lollipop.mediaflow.ui.theme.currentThemeColor
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow

/** 单个 Tab 的占位尺寸：它只是位置指示，不做成按钮，所以保持小巧。 */
private val TabSlotWidth = 20.dp
private val TabSlotHeight = 16.dp

/** Tab 图标尺寸。 */
private val TabIconSize = 12.dp

/** 胶囊内边距：指示器与容器边缘之间的呼吸空间。 */
private val TabBarPaddingHorizontal = 3.dp
private val TabBarPaddingVertical = 3.dp

/** 手势触发时胶囊上方弹出的圆形按钮：直径、与胶囊的间距，以及预留区域高度。 */
private val ScrollTopFabSize = 56.dp
private val ScrollTopFabGap = 12.dp
private val ScrollTopFabAreaHeight = ScrollTopFabSize + ScrollTopFabGap

/** fab 图标尺寸（比 Tab 图标更大更醒目）。 */
private val ScrollTopFabIconSize = 32.dp

/** 指示器缓动指数：Decelerate/AccelerateInterpolator(1.5) 等价于 3 次幂。 */
private const val TAB_EASING_POWER = 3F

/**
 * 底部悬浮 Tab 指示器（纯 Compose）。
 *
 * - 跟随 [PagerState] 的连续位置实时滑动，不做「落位后才补动画」的处理；
 * - 尺寸小巧，指示器与容器均为中性弱色，靠阴影与内容区分；
 * - 指示器形状复刻 View 版 TabBackgroundDrawable：左右边缘分别使用减速 / 加速缓动，
 *   滑动时先被拉长到下一个 Tab，再收缩回胶囊，一次滑动跨过多个 Tab 时同样逐格生效；
 * - 按压整个胶囊后，胶囊正上方会以「缩放 + 透明度」过渡弹出一个 56dp × 56dp 的圆形 fab；
 *   手指滑动到 fab 上时给一次震动提示（移出再移入会再次震动），此期间列表不滚动；
 *   仅在 fab 上松手时才回调 [onScrollToTop]，其余情况（未滑动即松手 / 未到达 fab 即松手 /
 *   到达 fab 后移开再松手）都不产生任何效果。
 *
 * 布局上预留了一段固定高度的透明区域承载 fab：既保证 fab 有完整的 56dp 方形空间（不被胶囊
 * 高度挤压变形），又保证胶囊的位置永远固定，不会因 fab 的出现 / 消失而上下跳动。
 */
@Composable
fun HomeTabBar(
    pages: List<HomePage>,
    pagerState: PagerState,
    onScrollToTop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColor = currentThemeColor()
    // 指示器：中性弱色（跟随文字色降透明度），深浅色主题下都能看见但不抢眼
    val indicatorColor = themeColor.buttonText.copy(alpha = 0.16F)

    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current
    // 回调可能随重组变化，用 rememberUpdatedState 保证手势里读到的始终是最新引用
    val currentOnScrollToTop by rememberUpdatedState(onScrollToTop)

    // fab 命中判定所需的像素尺寸（与下面的布局取值保持一致）
    val fabSizePx = with(density) { ScrollTopFabSize.toPx() }
    val fabGapPx = with(density) { ScrollTopFabGap.toPx() }

    // 是否处于按压状态：驱动 fab 的「缩放 + 透明度」过渡
    var pressed by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 预留 fab 区域：固定 56dp 宽 × (56dp + 间距) 高，fab 顶部对齐
        Box(
            modifier = Modifier
                .width(ScrollTopFabSize)
                .height(ScrollTopFabAreaHeight),
            contentAlignment = Alignment.TopCenter
        ) {
            ScrollTopFab(
                visible = pressed,
                containerColor = themeColor.buttonBackground,
                contentColor = themeColor.buttonText
            )
        }

        // 胶囊本体：按压手势从整个胶囊开始，向上滑动到 fab 触发震动提示
        Box(
            modifier = Modifier
                .shadow(elevation = 8.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(themeColor.buttonBackground)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        pressed = true
                        try {
                            // 上一帧是否已经落在 fab 上：保证「移入才震动、停留不重复、移出再移入再震动」
                            var hitFab = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    // 松手：只有当前仍在 fab 上才滚动到顶部
                                    if (hitFab) {
                                        currentOnScrollToTop()
                                    }
                                    break
                                }
                                val radius = fabSizePx / 2F
                                val centerX = size.width / 2F
                                val centerY = -fabGapPx - radius
                                val dx = change.position.x - centerX
                                val dy = change.position.y - centerY
                                val inside = dx * dx + dy * dy <= radius * radius
                                if (inside && !hitFab) {
                                    hapticFeedback.performHapticFeedback(
                                        HapticFeedbackType.ContextClick
                                    )
                                }
                                hitFab = inside
                                // 消费事件，确保手势期间列表不滚动
                                change.consume()
                            }
                        } finally {
                            pressed = false
                        }
                    }
                }
                .drawBehind {
                    drawTabIndicator(
                        // 连续页码：整数代表完全落在某一页，小数代表滑动过程中的页间位置。
                        // 在 draw 阶段直接读取 PagerState，滑动时会随快照变化实时重绘，无延迟。
                        position = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                        pageCount = pages.size,
                        slotWidth = TabSlotWidth.toPx(),
                        slotHeight = TabSlotHeight.toPx(),
                        paddingHorizontal = TabBarPaddingHorizontal.toPx(),
                        paddingVertical = TabBarPaddingVertical.toPx(),
                        color = indicatorColor
                    )
                }
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = TabBarPaddingHorizontal,
                    vertical = TabBarPaddingVertical
                )
            ) {
                pages.forEach { page ->
                    Box(
                        modifier = Modifier.size(TabSlotWidth, TabSlotHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(page.tabIconRes()),
                            contentDescription = null,
                            tint = themeColor.buttonText,
                            modifier = Modifier.size(TabIconSize)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 胶囊上方弹出的滚回顶部 fab。单独抽成非 [ColumnScope] 的 Composable，
 * 以便使用顶层（非 ColumnScope 扩展）的 [AnimatedVisibility] 重载。
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
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                modifier = Modifier.size(ScrollTopFabIconSize)
            )
        }
    }
}

private fun HomePage.tabIconRes(): Int {
    return when (this) {
        HomePage.PublicVideo -> R.drawable.movie_24
        HomePage.PublicPhoto -> R.drawable.photo_24
        HomePage.PrivateVideo -> R.drawable.movie_mask_24
        HomePage.PrivatePhoto -> R.drawable.photo_mask_24
    }
}

/**
 * 绘制指示器。
 *
 * 锚点取连续页码落在的相邻两格，[position] 的小数部分即“拉长”过程的进度：
 * 左右边缘分别使用 Decelerate/AccelerateInterpolator(1.5) 的缓动
 * （1-(1-t)^3 与 t^3），上下边缘保持不动，于是远端边缘先走、近端边缘后走，
 * 胶囊先被拉长、随后收缩到目标 Tab。
 */
private fun DrawScope.drawTabIndicator(
    position: Float,
    pageCount: Int,
    slotWidth: Float,
    slotHeight: Float,
    paddingHorizontal: Float,
    paddingVertical: Float,
    color: Color
) {
    if (pageCount <= 0 || slotWidth <= 0F || slotHeight <= 0F) {
        return
    }
    val current = position.coerceIn(0F, (pageCount - 1).toFloat())
    val fromIndex = floor(current).toInt().coerceIn(0, pageCount - 1)
    // 最后一格没有“下一格”，退化为原地显示
    val toIndex = (fromIndex + 1).coerceAtMost(pageCount - 1)
    val progress = (current - fromIndex).coerceIn(0F, 1F)

    val fromLeft = paddingHorizontal + fromIndex * slotWidth
    val fromRight = fromLeft + slotWidth
    val toLeft = paddingHorizontal + toIndex * slotWidth

    val left: Float
    val right: Float
    if (fromIndex == toIndex) {
        left = fromLeft
        right = fromRight
    } else {
        val startProgress = 1F - (1F - progress).pow(TAB_EASING_POWER)
        val endProgress = progress.pow(TAB_EASING_POWER)
        left = (toLeft - fromLeft) * endProgress + fromLeft
        right = (toLeft + slotWidth - fromRight) * startProgress + fromRight
    }

    val width = right - left
    if (width <= 0F) {
        return
    }
    val radius = min(slotHeight, width) / 2F
    drawRoundRect(
        color = color,
        topLeft = Offset(left, paddingVertical),
        size = Size(width, slotHeight),
        cornerRadius = CornerRadius(radius, radius)
    )
}
