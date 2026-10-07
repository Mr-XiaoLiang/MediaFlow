package com.lollipop.mediaflow.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
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

/** 指示器缓动指数：Decelerate/AccelerateInterpolator(1.5) 等价于 3 次幂。 */
private const val TAB_EASING_POWER = 3F

/**
 * 底部悬浮 Tab 指示器（纯 Compose，只作位置指示，不接受点击）。
 *
 * - 跟随 [PagerState] 的连续位置实时滑动，不做「落位后才补动画」的处理；
 * - 尺寸小巧，指示器与容器均为中性弱色，靠阴影与内容区分；
 * - 指示器形状复刻 View 版 TabBackgroundDrawable：左右边缘分别使用减速 / 加速缓动，
 *   滑动时先被拉长到下一个 Tab，再收缩回胶囊，一次滑动跨过多个 Tab 时同样逐格生效。
 */
@Composable
fun HomeTabBar(
    pages: List<HomePage>,
    pagerState: PagerState,
    modifier: Modifier = Modifier
) {
    val themeColor = currentThemeColor()
    // 指示器：中性弱色（跟随文字色降透明度），深浅色主题下都能看见但不抢眼
    val indicatorColor = themeColor.buttonText.copy(alpha = 0.16F)

    Box(
        modifier = modifier
            .shadow(elevation = 8.dp, shape = CircleShape)
            .clip(CircleShape)
            .background(themeColor.buttonBackground)
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
