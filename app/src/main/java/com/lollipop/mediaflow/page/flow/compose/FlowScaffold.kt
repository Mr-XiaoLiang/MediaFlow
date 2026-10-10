package com.lollipop.mediaflow.page.flow.compose

import android.view.Gravity
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.page.flow.ScreenRotate
import com.lollipop.mediaflow.ui.home.menu.ComposePopupMenuAnchor
import com.lollipop.mediaflow.ui.home.menu.rememberComposePopupMenu
import com.lollipop.mediaflow.ui.home.plainTextStyle
import com.lollipop.mediaflow.ui.theme.ThemeColor
import com.lollipop.mediaflow.ui.theme.currentThemeColor

/**
 * Flow 页的纯 Compose 外壳（与旧 View 外壳 `activity_flow.xml` + `BasicFlowActivity` 平行）。
 *
 * 对照关系：
 * - `decorationPanel` + 4 条 Guideline → 本文件的 [FlowDecorationLayer]（16dp 外边距 + insets）；
 * - `menuBar` / `menuBarBlur` → [FlowMenuBar]（**纯色底**，模糊已下线）；
 * - `backBtn` / `backBtnBlur` → [FlowRoundIconButton]（纯色底，模糊已下线）；
 * - `titleView` / `tagGroup` → [FlowTitle] / [FlowTags]；
 * - `sidePanel` 容器 → 右侧 42dp 槽位（内容由 `sidePanel` 插槽提供）；
 * - `sidePanelGestureView` → [Modifier.sidePanelGesture]；
 * - `drawerLayout` / `drawerPanel` / `menuBtn` → **已下线**（抽屉宫格切换移除）。
 */
@Composable
fun FlowScaffold(
    state: FlowShellState,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onSelectRotate: (ScreenRotate) -> Unit,
    onSidePanelChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    sidePanel: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    val insets = rememberFlowInsets()
    Box(
        modifier = modifier
            .fillMaxSize()
            // 视频 / 图片内容铺满整屏，装饰元素浮在其上
            .background(Color.Black)
    ) {
        content()

        if (state.isSidePanelVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(SidePanelWidth)
                    .padding(
                        top = insets.top,
                        end = insets.end,
                        bottom = insets.bottom,
                    )
            ) {
                sidePanel()
            }
        }

        if (state.sidePanelGestureEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(SidePanelGestureHeightPercent)
                    .sidePanelGesture(
                        onSwipeLeft = { onSidePanelChange(true) },
                        onSwipeRight = { onSidePanelChange(false) },
                    )
            )
        }

        if (state.isDecorationVisible) {
            FlowDecorationLayer(
                state = state,
                padding = PaddingValues(
                    start = insets.start + GuideMargin,
                    top = insets.top + GuideMargin,
                    // 侧栏展开时右边界让给侧栏本身（对齐旧外壳 changeSidePanel 的 guideEnd 处理）
                    end = insets.end + if (state.isSidePanelVisible) 0.dp else GuideMargin,
                    bottom = insets.bottom + GuideMargin,
                ),
                onBack = onBack,
                onToggleFullscreen = onToggleFullscreen,
                onSelectRotate = onSelectRotate,
                onSidePanelChange = onSidePanelChange,
            )
        }
    }
}

/**
 * 装饰层：返回键（左）/ 菜单栏（右）/ 标题与标签（中间）。
 *
 * 显隐规则在 [FlowMenuBar] 与 [FlowTitle] 处集中计算，对应旧外壳的
 * `VisibleFilterGroup.Or(menuBar)` 与各 `PipVisibleFilter` 的组合语义。
 */
@Composable
private fun FlowDecorationLayer(
    state: FlowShellState,
    padding: PaddingValues,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onSelectRotate: (ScreenRotate) -> Unit,
    onSidePanelChange: (Boolean) -> Unit
) {
    val themeColor = currentThemeColor()
    val isPip = state.isPipMode
    val showBack = state.showBackButton && !isPip
    val showTitle = state.showTitle && !isPip && state.title.isNotEmpty()
    val showTags = state.showTags && !isPip && state.tags.isNotEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            if (showBack) {
                FlowRoundIconButton(
                    icon = R.drawable.arrow_back_24,
                    description = null,
                    containerColor = themeColor.buttonBackground,
                    contentColor = themeColor.buttonText,
                    onClick = onBack,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1F)
                    .padding(horizontal = TitleHorizontalMargin),
                verticalArrangement = Arrangement.Center
            ) {
                if (showTitle) {
                    FlowTitle(state = state)
                }
                if (showTags) {
                    FlowTags(tags = state.tags)
                }
            }
            FlowMenuBar(
                state = state,
                themeColor = themeColor,
                onToggleFullscreen = onToggleFullscreen,
                onSelectRotate = onSelectRotate,
                onSidePanelChange = onSidePanelChange,
            )
        }
    }
}

/**
 * 菜单栏（胶囊底 + 按钮组）。
 *
 * 与旧外壳的差异：**不再有模糊层**；容器底色沿用 `button_background`，
 * 形状按 `RoundOutlineLayout` 的默认 `gravity = 0` 复刻为「高度一半」的胶囊圆角。
 */
@Composable
private fun FlowMenuBar(
    state: FlowShellState,
    themeColor: ThemeColor,
    onToggleFullscreen: () -> Unit,
    onSelectRotate: (ScreenRotate) -> Unit,
    onSidePanelChange: (Boolean) -> Unit
) {
    val isPip = state.isPipMode
    val showRotate = state.showRotateButton && !isPip
    val showFullscreen = state.showFullscreenButton && !isPip && state.isPortrait
    val showSidePanel = state.showSidePanelButton && !isPip
    if (!showRotate && !showFullscreen && !showSidePanel) {
        return
    }
    Surface(
        shape = RoundedCornerShape(MenuBarHeight / 2),
        color = themeColor.buttonBackground,
        modifier = Modifier.height(MenuBarHeight)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MenuBarHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showRotate) {
                FlowRotateButton(
                    state = state,
                    themeColor = themeColor,
                    onSelectRotate = onSelectRotate,
                )
            }
            if (showFullscreen) {
                FlowRoundIconButton(
                    icon = if (state.isFullscreen || !state.isPortrait) {
                        R.drawable.fullscreen_exit_24
                    } else {
                        R.drawable.fullscreen_24
                    },
                    description = null,
                    containerColor = Color.Transparent,
                    contentColor = themeColor.buttonText,
                    onClick = onToggleFullscreen,
                )
            }
            if (showSidePanel) {
                FlowRoundIconButton(
                    icon = if (state.isSidePanelVisible) {
                        R.drawable.right_panel_close_24
                    } else {
                        R.drawable.right_panel_open_24
                    },
                    description = null,
                    containerColor = Color.Transparent,
                    contentColor = themeColor.buttonText,
                    onClick = { onSidePanelChange(!state.isSidePanelVisible) },
                )
            }
        }
    }
}

/** 旋转按钮：点击弹出四种模式的 Compose 气泡菜单（复用首页的 `ComposePopupMenu`）。 */
@Composable
private fun FlowRotateButton(
    state: FlowShellState,
    themeColor: ThemeColor,
    onSelectRotate: (ScreenRotate) -> Unit
) {
    val menuState = rememberComposePopupMenu { builder ->
        ScreenRotate.entries.forEach { mode ->
            builder.addMenu(tag = mode.name, titleRes = mode.label, iconRes = mode.icon)
        }
        builder
            .gravity(Gravity.END)
            .offsetDp(0, 8)
            .onClick { item ->
                val mode = ScreenRotate.findByName(item.tag) ?: ScreenRotate.ROTATE_LOCK
                onSelectRotate(mode)
            }
    }
    ComposePopupMenuAnchor(state = menuState) {
        FlowRoundIconButton(
            icon = state.screenRotate.icon,
            description = null,
            containerColor = Color.Transparent,
            contentColor = themeColor.buttonText,
            onClick = { menuState.show() },
        )
    }
}

/**
 * 圆形/胶囊图标按钮。
 *
 * 尺寸对齐 `activity_flow.xml`：42×36dp 的点击区，向内 6dp 内边距后再绘制图标。
 */
@Composable
private fun FlowRoundIconButton(
    @DrawableRes icon: Int,
    description: String?,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = MenuButtonWidth, height = MenuBarHeight)
            .background(color = containerColor, shape = RoundedCornerShape(MenuBarHeight / 2))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = contentColor,
            modifier = Modifier
                .size(IconSize)
                .padding(MenuButtonIconPadding),
        )
    }
}

/**
 * 标题。
 *
 * 对齐 `titleView`：白色 + 黑色阴影（radius 3px）、14sp；
 * 跑马灯模式下单行横向滚动，否则最多两行尾部省略。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FlowTitle(state: FlowShellState) {
    val style = remember {
        plainTextStyle(14.sp).copy(
            shadow = Shadow(color = Color.Black, blurRadius = 3F)
        )
    }
    if (state.isMarqueeTitle) {
        Text(
            text = state.title,
            modifier = Modifier.basicMarquee(),
            style = style,
            color = Color.White,
            maxLines = 1,
        )
    } else {
        Text(
            text = state.title,
            style = style,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 标签组：自动换行，间距对齐 `tagGroup` 的子项外边距。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowTags(tags: List<FlowTag>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(TagHorizontalSpacing),
        verticalArrangement = Arrangement.spacedBy(TagVerticalSpacing),
    ) {
        tags.forEach { tag ->
            FlowTagItem(tag)
        }
    }
}

/**
 * 单个标签：复刻 [com.lollipop.common.ui.view.TagTextView] 的「底块挖空文字」效果。
 *
 * 实现要点：先用 `android:color`（半透明白）画圆角底块，再用 [BlendMode.DstOut] 绘制文字，
 * 把文字覆盖处从底块中挖掉；离屏合成（[CompositingStrategy.Offscreen]）保证挖空只作用于标签自身。
 */
@Composable
private fun FlowTagItem(tag: FlowTag) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = remember { plainTextStyle(11.sp, lineHeightRatio = 1F) }
    val layout: TextLayoutResult = remember(tag.text, style) {
        measurer.measure(text = tag.text, style = style)
    }
    Canvas(
        modifier = Modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .size(
                width = with(density) { layout.size.width.toDp() } + TagPaddingHorizontal * 2,
                height = with(density) { layout.size.height.toDp() },
            )
    ) {
        drawRoundRect(
            color = TagBackgroundColor,
            cornerRadius = CornerRadius(TagCornerRadius.toPx()),
        )
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(TagPaddingHorizontal.toPx(), 0F),
            blendMode = BlendMode.DstOut,
        )
    }
}

/**
 * 侧栏显隐手势。
 *
 * 对标 [com.lollipop.common.ui.view.SimpleGestureLayout]：仅横向拖动参与识别
 * （`detectHorizontalDragGestures` 自带 touchSlop 与方向判定），松手时按总位移方向回调。
 * 手势层只覆盖顶部 30% 高度（对齐 `sidePanelGestureView` 的 `constraintHeight_percent`），
 * 因此不会与页面的纵向翻页 / 上下滑动争夺事件。
 */
private fun Modifier.sidePanelGesture(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
): Modifier {
    return pointerInput(onSwipeLeft, onSwipeRight) {
        val threshold = viewConfiguration.touchSlop
        var total = 0F
        detectHorizontalDragGestures(
            onDragStart = { total = 0F },
            onHorizontalDrag = { _, dragAmount -> total += dragAmount },
            onDragEnd = {
                if (total <= -threshold) {
                    onSwipeLeft()
                } else if (total >= threshold) {
                    onSwipeRight()
                }
            }
        )
    }
}

/** 装饰层外边距：对齐 Guideline 的 `constraintGuide_begin/end = 16dp`。 */
private val GuideMargin = 16.dp

/** 菜单栏高度（`menuBar` 的 36dp）。 */
private val MenuBarHeight = 36.dp

/** 菜单按钮宽度（`rotateBtn` 等 42dp）。 */
private val MenuButtonWidth = 42.dp

/** 菜单栏左右内边距（`paddingHorizontal = 4dp`）。 */
private val MenuBarHorizontalPadding = 4.dp

/** 图标绘制尺寸。 */
private val IconSize = 24.dp

/** 菜单按钮图标内边距（`padding = 6dp`）。 */
private val MenuButtonIconPadding = 6.dp

/** 侧栏宽度（`sidePanel` 的 42dp）。 */
private val SidePanelWidth = 42.dp

/** 侧栏手势层高度占比（`constraintHeight_percent = 0.3`）。 */
private const val SidePanelGestureHeightPercent = 0.3F

/** 标题左右外边距（`layout_marginHorizontal = 16dp`）。 */
private val TitleHorizontalMargin = 16.dp

/** 标签底块颜色（`android:color = #DDFFFFFF`）。 */
private val TagBackgroundColor = Color(0xDDFFFFFF)

/** 标签圆角（`android:radius = 4dp`）。 */
private val TagCornerRadius = 4.dp

/** 标签左右内边距（`paddingHorizontal = 4dp`）。 */
private val TagPaddingHorizontal = 4.dp

/** 标签横向间距（`layout_marginEnd = 6dp`）。 */
private val TagHorizontalSpacing = 6.dp

/** 标签纵向间距（`layout_marginTop = 2dp`）。 */
private val TagVerticalSpacing = 2.dp

/**
 * Flow 页使用的 insets（Compose 自洽口径，Dp 四边）。
 */
private data class FlowInsets(
    val start: Dp,
    val top: Dp,
    val end: Dp,
    val bottom: Dp
)

/**
 * 读取 Flow 页 insets：`systemBars ∪ displayCutout` 逐边取 max。
 *
 * 口径与 View 分支 [com.lollipop.common.ui.page.ViewInsetsActivity] 的 `findInsets` 一致
 * （不含 IME / systemGestures），但走 Compose 自洽的 [WindowInsets]：
 * 两侧实现分属不同分支，互不依赖、也不共享监听器。
 */
@Composable
private fun rememberFlowInsets(): FlowInsets {
    val padding = WindowInsets.systemBars
        .union(WindowInsets.displayCutout)
        .asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    return with(LocalDensity.current) {
        FlowInsets(
            start = padding.calculateStartPadding(layoutDirection),
            top = padding.calculateTopPadding(),
            end = padding.calculateEndPadding(layoutDirection),
            bottom = padding.calculateBottomPadding()
        )
    }
}
