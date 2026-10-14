package com.lollipop.mediaflow.ui.home.menu

import android.view.Gravity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import com.lollipop.mediaflow.ui.effect.LocalOverlayHost
import com.lollipop.mediaflow.ui.effect.effectSurfaceFromContext
import com.lollipop.mediaflow.ui.theme.currentThemeColor

/** 气泡阴影高度：保证在白色背景上也能看出边界。 */
private val MenuShadowElevation = 8.dp

/** 气泡容器圆角（对齐 View 版 `menu_popup_background`）。 */
private val MenuCornerRadius = 16.dp

/**
 * 气泡菜单的展示状态：与 [ComposePopupMenu] 分离，便于用 remember 复用菜单描述，
 * 而开关状态随组合生命周期管理。
 */
@Stable
class ComposePopupMenuState internal constructor(
    internal val menu: ComposePopupMenu
) {

    var isOpen by mutableStateOf(false)
        private set

    /** 锚点在窗口坐标系中的矩形，由 [ComposePopupMenuAnchor] 持续更新。 */
    internal var anchorBounds by mutableStateOf(IntRect.Zero)

    fun show() {
        isOpen = true
    }

    fun dismiss() {
        isOpen = false
    }
}

@Composable
fun rememberComposePopupMenu(build: (ComposePopupMenu.Builder) -> Unit): ComposePopupMenuState {
    val density = LocalDensity.current
    return remember(density) {
        ComposePopupMenuState(
            ComposePopupMenu.Builder(density).apply(build).build()
        )
    }
}

/**
 * 把 [content] 作为锚点，[state] 打开时在其下方弹出菜单。
 * content 自己负责触发 [ComposePopupMenuState.show]（例如点击图标）。
 *
 * ## 为什么不用 `Popup`
 *
 * Compose 的 `Popup` 是**独立 Window**（`PopupLayout` + `WindowManager`），
 * 既无法用 Haze 模糊（跨 Window 不能采样），也拿不到 Window 句柄做系统窗口模糊。
 * 因此这里改为**页面级浮层**（[LocalOverlayHost]，由
 * [com.lollipop.mediaflow.ui.BasicComposeActivity] 在页面根提供）：
 * 浮层与页面内容同处一个 Window，可以直接使用统一的特效修饰符。
 *
 * 菜单坐标取 [content] 自身的窗口坐标（[onGloballyPositioned]），
 * 因此弹出位置始终跟着被点击的那个元素。
 */
@Composable
fun ComposePopupMenuAnchor(
    state: ComposePopupMenuState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val overlayHost = LocalOverlayHost.current
    Box(modifier = modifier) {
        // 外层 Box 可能被父级（例如瀑布流的整行 item）撑满，因此再套一层只包裹内容的 Box，
        // 取它（真实内容）的窗口坐标作为菜单锚点，保证气泡贴着被点击的元素。
        Box(
            modifier = Modifier.onGloballyPositioned { coordinates ->
                state.anchorBounds = coordinates.boundsInWindow().roundToIntRect()
            }
        ) {
            content()
        }
    }
    if (overlayHost != null && state.isOpen) {
        LaunchedEffect(overlayHost, state) {
            overlayHost.show {
                ComposePopupMenuOverlay(
                    state = state,
                    onUnmounted = { overlayHost.dismiss() },
                )
            }
        }
    }
}

/**
 * 浮层内的气泡菜单：透明遮罩 + 按锚点定位的菜单卡片。
 *
 * 退场动画播完后回调 [onUnmounted]，由宿主移除浮层。
 */
@Composable
private fun ComposePopupMenuOverlay(
    state: ComposePopupMenuState,
    onUnmounted: () -> Unit,
) {
    val menu = state.menu
    val anchorBounds = state.anchorBounds
    // 承载进出场动画：关闭后仍保留到退场动画播完再卸载
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = state.isOpen

    if (!visibleState.currentState && !visibleState.targetState) {
        LaunchedEffect(Unit) { onUnmounted() }
        return
    }

    // 返回键关闭（原 Popup 由 focusable 属性代劳）
    BackHandler { state.dismiss() }

    // 以锚点的一角为缩放原点：END 对齐时从右上角展开，START 对齐时从左上角展开
    val transformOrigin = remember(menu.gravity) {
        if (menu.gravity == Gravity.END) {
            TransformOrigin(1F, 0F)
        } else {
            TransformOrigin(0F, 0F)
        }
    }
    val layoutDirection = LocalLayoutDirection.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val windowSize = IntSize(constraints.maxWidth, constraints.maxHeight)
        // 遮罩：点击任意空白处关闭（菜单卡片在其上层消费点击）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    state.dismiss()
                }
        )
        Box(
            modifier = Modifier.layout { measurable, constraints ->
                // 必须沿用父约束（maxWidth = 屏幕宽），与原先 Popup 的窗口约束一致。
                // 若丢成无界约束，菜单宽度会被 IntrinsicSize.Max / fillMaxWidth 撑满整屏，
                // 表现为「一个灰色的大框」。
                val placeable = measurable.measure(
                    constraints.copy(minWidth = 0, minHeight = 0)
                )
                val offset = menuPosition(
                    anchorBounds = anchorBounds,
                    gravity = menu.gravity,
                    offsetX = menu.offsetX,
                    offsetY = menu.offsetY,
                    windowSize = windowSize,
                    layoutDirection = layoutDirection,
                    popupContentSize = IntSize(placeable.width, placeable.height),
                )
                // 节点占满父容器、只把内容摆到目标位置：
                // 若节点尺寸等于内容尺寸，超出其边界的投影（阴影）会被裁掉或错位。
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(offset.x, offset.y)
                }
            }
        ) {
            AnimatedVisibility(
                visibleState = visibleState,
                enter = fadeIn(animationSpec = tween(150)) +
                        scaleIn(
                            animationSpec = tween(150),
                            initialScale = 0.92F,
                            transformOrigin = transformOrigin
                        ),
                exit = fadeOut(animationSpec = tween(100)) +
                        scaleOut(
                            animationSpec = tween(100),
                            targetScale = 0.92F,
                            transformOrigin = transformOrigin
                        )
            ) {
                ComposePopupMenuCard(state = state, menu = menu)
            }
        }
    }
}

/** 菜单卡片：统一特效背景 + 条目列表。 */
@Composable
private fun ComposePopupMenuCard(
    state: ComposePopupMenuState,
    menu: ComposePopupMenu
) {
    val themeColor = currentThemeColor()
    val shape = RoundedCornerShape(MenuCornerRadius)
    // 在组合期求值，因此过滤条件可以观察隐私开关等状态
    val visibleItems = menu.items.filter { item ->
        menu.filter?.invoke(item) ?: true
    }
    // 与悬浮按钮**完全同一条链路**：阴影 + 统一特效修饰符（纯色 → 模糊 → 半透明纯色）。
    // 这里刻意不使用 `Surface` 包裹——`Surface` 会引入自己的图层与背景绘制，
    // 是气泡菜单与按钮表现不一致的唯一来源。
    // IntrinsicSize.Max 让气泡宽度等于最宽条目。
    Column(
        modifier = Modifier
            // ⚠️ 顺序要求：**先定尺寸，再画阴影 / 背景**。
            // 若 `width(IntrinsicSize.Max)` 排在背景修饰符之后，背景会按「父约束（整屏）」
            // 绘制 —— 表现为气泡外面糊着一大块灰色框。
            .width(IntrinsicSize.Max)
            .shadow(elevation = MenuShadowElevation, shape = shape)
            .effectSurfaceFromContext(
                shape = shape,
                fallbackColor = themeColor.buttonBackground,
            )
            .padding(vertical = 8.dp)
    ) {
        visibleItems.forEach { item ->
            ComposePopupMenuItem(
                item = item,
                onClick = {
                    state.dismiss()
                    menu.onClick(item)
                }
            )
        }
    }
}

/**
 * 菜单条目：对齐 View 版 item_menu_pop.xml
 * （内边距 9dp、24dp 图标 + 8dp 间距、BodyLarge 文本、点击波纹背景）。
 */
@Composable
private fun ComposePopupMenuItem(
    item: ComposePopupMenu.Item,
    onClick: () -> Unit
) {
    val themeColor = currentThemeColor()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.iconRes != 0) {
            Icon(
                painter = painterResource(item.iconRes),
                contentDescription = null,
                tint = themeColor.buttonText,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(24.dp)
            )
        }
        Text(
            text = stringResource(item.titleRes),
            style = MaterialTheme.typography.bodyLarge,
            color = themeColor.buttonText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 计算菜单左上角在窗口中的位置：END 对齐时右边缘贴齐锚点右侧，否则左边缘对齐；
 * 纵向贴在锚点下方，并夹取到窗口范围内。
 */
private fun menuPosition(
    anchorBounds: IntRect,
    gravity: Int,
    offsetX: Int,
    offsetY: Int,
    windowSize: IntSize,
    layoutDirection: LayoutDirection,
    popupContentSize: IntSize
): IntOffset {
    val startX = if (gravity == Gravity.END) {
        anchorBounds.right - popupContentSize.width
    } else {
        anchorBounds.left
    }
    val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
    val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
    return IntOffset(
        (startX + offsetX).coerceIn(0, maxX),
        (anchorBounds.bottom + offsetY).coerceIn(0, maxY)
    )
}
