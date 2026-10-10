package com.lollipop.mediaflow.page.flow.compose

import android.view.Gravity
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

/**
 * Flow 页的纯 Compose 外壳（脚手架）。
 *
 * ## 四层结构
 *
 * 外壳只提供 **4 个空容器**，内容全部由调用方填充；容器在各阶段的形态变化由本文件负责：
 *
 * | 层 | 容器行为 | 默认 |
 * | --- | --- | --- |
 * | `background` 背景层 | 全屏，随侧栏进度做视差平移 | 无填充（露出窗口背景色） |
 * | `content` 内容区（`FlowContentSide`） | 过渡期间整层淡出；让位宽度在动画结束后一次性切换 | — |
 * | `sidePanel` 侧栏层 | 右侧固定宽度槽位，平移进入 / 退出 | 无填充 |
 * | `controller` 控制器层 | 逐帧右侧让位 | 无填充（Flow 页填 [FlowController]） |
 *
 * 阶段信号通过 [FlowShellState] 暴露（如 `isContentSuppressed`：内容即将隐藏，
 * 页面据此暂停播放、刷新底图素材）；动画进度 [FlowShellState.panelProgress]
 * **约定只在绘制阶段读取**。
 *
 * ## 与旧 View 外壳的对照
 *
 * - `decorationPanel` + 4 条 Guideline → 本文件的 [FlowController]（16dp 外边距 + insets）；
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
    /** 内容区（业务主体）：让位与淡出由外壳负责。 */
    content: @Composable BoxScope.() -> Unit,
    /** 侧栏显隐请求（菜单栏按钮与手势共用；页面据此更新自身状态）。 */
    onSidePanelChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** 背景层：默认无填充（露出窗口背景色）；平移由外壳负责。 */
    background: @Composable BoxScope.() -> Unit = {},
    /** 侧栏层：平移进入 / 退出由外壳负责。 */
    sidePanel: @Composable BoxScope.() -> Unit = {},
    /** 控制器层（返回键 / 标题 / 菜单栏）：Flow 页填 [FlowController]。 */
    controller: @Composable BoxScope.() -> Unit = {},
) {
    val insets = rememberFlowInsets()

    // ---- 侧栏开关过渡（四层结构，各层只承担自己的那次变化） ----
    // 时序：① 内容层淡出（页面同时暂停）→ ② 让位 + 平移动画（背景与侧栏位移动画、
    //       控制器逐帧让位）→ ③ 切换内容区的让位宽度（唯一一次重排：画面不可见、
    //       屏幕无动画）→ ④ 等一帧 → ⑤ 内容层淡入 → 按过渡前的播放态恢复。
    val panelProgress = remember { Animatable(if (state.isSidePanelVisible) 1F else 0F) }

    /** 侧栏是否仍在组合树中（收起动画跑完后才移除，否则看不到滑出）。 */
    var isPanelAttached by remember { mutableStateOf(state.isSidePanelVisible) }

    /**
     * 内容区是否已让出侧栏宽度（一次性尺寸切换）。
     *
     * 只在本文件的动画结束后翻转，因此内容区（Pager / 网格 / 视频 Surface）在动画期间
     * 约束不变，不会逐帧重排。
     */
    var isLayoutShifted by remember { mutableStateOf(false) }

    /** 内容区整体透明度：置位时淡出（在绘制阶段读取，不触发内容区重组）。 */
    val contentAlpha = remember { Animatable(if (state.isContentSuppressed) 0F else 1F) }

    val panelWidthPx = with(LocalDensity.current) { SidePanelWidth.toPx() }

    // 进度外露（只写不读消费）：供需要联动的图层在绘制阶段取用
    LaunchedEffect(Unit) {
        snapshotFlow { panelProgress.value }.collect { state.panelProgress = it }
    }
    LaunchedEffect(state.isContentSuppressed) {
        contentAlpha.animateTo(
            targetValue = if (state.isContentSuppressed) 0F else 1F,
            animationSpec = tween(
                durationMillis = FlowShellState.PanelContentFadeMs,
                easing = FastOutSlowInEasing,
            )
        )
    }
    LaunchedEffect(state.isSidePanelVisible) {
        val expanded = state.isSidePanelVisible
        val target = if (expanded) 1F else 0F
        if (panelProgress.value == target) {
            // 首次组合（或状态本就一致）时不做过渡
            return@LaunchedEffect
        }
        try {
            // ① 内容层淡出（页面据此暂停播放、刷新底图素材）；此时内容区仍是「动画前」的让位
            state.isContentSuppressed = true
            delay(FlowShellState.PanelContentFadeMs.toLong())
            isPanelAttached = true
            // ② 让位 + 平移：背景与侧栏位移动画、控制器逐帧跟随；内容区尺寸不动
            panelProgress.animateTo(
                targetValue = target,
                animationSpec = tween(
                    durationMillis = FlowShellState.PanelShiftMs,
                    // iOS 风格：慢入慢出（cubic-bezier(0.4, 0, 0.2, 1)），
                    // 有明显速度变化才看得清"移动"
                    easing = FastOutSlowInEasing
                )
            )
            isPanelAttached = expanded
            // ③ 切换内容区的让位：唯一一次重排（画面不可见、屏幕无动画）
            isLayoutShifted = expanded
            // ④ 等一帧，确保测量 / 布局完成，之后才让内容淡入
            withFrameNanos { }
        } finally {
            // ⑤ 被快速连点打断时也要恢复内容，避免内容长期不可见
            state.isContentSuppressed = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // ① 背景层：全屏铺底，内容随侧栏进度做视差平移（见 [FlowBackgroundLayer]）
        FlowBackgroundLayer(state = state, content = background)

        // ② 内容区：过渡期间整层淡出，让位在动画结束后一次性切换（见 [FlowContentSide]）
        FlowContentSide(
            isLayoutShifted = isLayoutShifted,
            alpha = { contentAlpha.value },
            content = content,
        )

        // ③ 控制器层：逐帧右侧让位（本层内部处理 insets 与让位宽度）
        if (state.isDecorationVisible) {
            controller()
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

        // ④ 侧栏层：平移进入 / 退出。`translationX` 放在 graphicsLayer 的绘制 lambda 里读，
        // 因此动画只触发重绘，不会引起侧栏内部（LazyColumn / 缩略图）重组或重新布局。
        if (isPanelAttached) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(SidePanelWidth)
                    .graphicsLayer {
                        translationX = (1F - panelProgress.value) * panelWidthPx
                    }
                    .padding(
                        top = insets.top,
                        end = insets.end,
                        bottom = insets.bottom,
                    )
            ) {
                sidePanel()
            }
        }
    }
}

/**
 * 背景层容器。
 *
 * **冗余渲染**：布局宽度取「屏宽 + 一个侧栏宽」（右侧冗余）并左对齐，平移量取
 * `−侧栏宽 × 进度`。两端因此恰好落在：
 * - 收起（进度 0）：`[0, 屏宽 + 侧栏宽]`；
 * - 展开（进度 1）：`[−侧栏宽, 屏宽]`。
 *
 * 任意中间进度下屏幕 `[0, 屏宽]` 都被**完整覆盖**——既不会露出背景自身的裁切边缘
 * （否则观感是「一张被裁的图在滑动」），也不会露出底色。
 *
 * 与内容区页内背景的一致性：展开完成后背景的可见区（`[0, 屏宽 − 侧栏宽]`）与内容区
 * 完全重合；`ContentScale.Crop` 按长边等比放大，因此竖屏（长边 = 高度）下两者的
 * 缩放与中心**完全一致**，内容淡入交叉时图案不会错位。横屏（长边 = 宽度）下缩放存在
 * `(屏宽 + 侧栏宽) / (屏宽 − 侧栏宽)` 的差异，模糊图 + 交叉淡入下不易察觉（已知取舍）。
 *
 * 平移量在 [Modifier.graphicsLayer] 的绘制 lambda 内读取，动画期间只重绘、不重排。
 */
@Composable
private fun FlowBackgroundLayer(
    state: FlowShellState,
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                // 右侧冗余：本层动画方向是「右 → 左」，只有右侧需要多出的覆盖范围
                .width(maxWidth + SidePanelWidth)
                .fillMaxHeight()
                .graphicsLayer {
                    // 绘制阶段读取：只重绘、不重组
                    translationX = -SidePanelWidth.toPx() * state.panelProgress
                }
        ) {
            content()
        }
    }
}

/**
 * 内容区容器（FlowContentSide）。
 *
 * 两层变化互相独立、都交给外壳：
 * - **淡出**：[alpha] 在绘制阶段读取，动画期间只重绘，内容区不重组；
 * - **让位**：[isLayoutShifted] 由外壳在移动动画结束后翻转，因此内容区（Pager / 网格 /
 *   视频 Surface）在动画期间保持原尺寸，只重排一次。
 */
@Composable
private fun FlowContentSide(
    isLayoutShifted: Boolean,
    alpha: () -> Float,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(end = if (isLayoutShifted) SidePanelWidth else 0.dp)
            .graphicsLayer { this.alpha = alpha() }
    ) {
        content()
    }
}

/**
 * 控制器层：返回键（左）/ 菜单栏（右）/ 标题与标签（中间）。
 *
 * 显隐规则在 [FlowMenuBar] 与 [FlowTitle] 处集中计算，对应旧外壳的
 * `VisibleFilterGroup.Or(menuBar)` 与各 `PipVisibleFilter` 的组合语义。
 * Flow 页把它填进 [FlowScaffold] 的 `controller` 插槽；需要整层替换的页面可自行提供内容。
 */
@Composable
fun FlowController(
    state: FlowShellState,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onSelectRotate: (ScreenRotate) -> Unit,
    onSidePanelChange: (Boolean) -> Unit
) {
    val insets = rememberFlowInsets()
    val themeColor = currentThemeColor()
    val isPip = state.isPipMode
    val showBack = state.showBackButton && !isPip
    val showTitle = state.showTitle && !isPip && state.title.isNotEmpty()
    val showTags = state.showTags && !isPip && state.tags.isNotEmpty()

    // 让位宽度**逐帧**跟随侧栏进度：控制器只有 6~8 个节点，逐帧重排的成本可忽略，
    // 换来的是「标题 / 菜单栏随侧栏推进连续平移」而不是突然跳到新位置。
    // 注意：进度在这里读取，因此只重组本层，不会连累内容区。
    val padding = PaddingValues(
        start = insets.start + GuideMargin,
        top = insets.top + GuideMargin,
        end = insets.end + GuideMargin + SidePanelWidth * state.panelProgress,
        bottom = insets.bottom + GuideMargin,
    )

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
            // 注意顺序：先撑满按钮区（42×36dp）、再内缩 padding，图标才不会被扣小；
            // 若写成 size(24.dp).padding(6.dp) 会被内缩到只剩 12dp（图标过小）
            modifier = Modifier
                .fillMaxSize()
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
            // 旧 XML 中每个 tag 都有 `layout_marginTop = 2dp`，单行时同样生效
            // （FlowRow 的 verticalArrangement 只在多行之间产生间距，替代不了它）
            .padding(top = TagMarginTop)
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

/** 菜单按钮图标内边距（`padding = 6dp`，作用在 42×36dp 按钮区上）。 */
private val MenuButtonIconPadding = 6.dp

/** 侧栏宽度：与页面的过渡底图共用同一常量（见 [FlowShellState.SidePanelWidth]）。 */
private val SidePanelWidth = FlowShellState.SidePanelWidth

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

/** 标签纵向间距（多行之间；旧版每个 tag 自带 `marginTop`）。 */
private val TagVerticalSpacing = 2.dp

/** 标签上外边距（旧 `layout_marginTop = 2dp`，单行时也需生效）。 */
private val TagMarginTop = 2.dp

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
