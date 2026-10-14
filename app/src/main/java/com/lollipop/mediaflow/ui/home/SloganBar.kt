package com.lollipop.mediaflow.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** Tab 轮廓线高亮描边宽度。 */
private val TabOutlineStrokeWidth = 3.dp

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
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onBarHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val customValue = remember(reloadTick) {
        Preferences.customSloganValue.get()
    }
    val themeColor = currentThemeColor()
    val density = LocalDensity.current

    // 回调可能随重组变化，用 rememberUpdatedState 保证回调里读到的始终是最新引用
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentOnBarHeightChanged by rememberUpdatedState(onBarHeightChanged)

    val strokeWidthPx = with(density) { TabOutlineStrokeWidth.toPx() }

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
