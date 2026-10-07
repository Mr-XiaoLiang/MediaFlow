package com.lollipop.mediaflow.ui.home.menu

import android.view.Gravity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.lollipop.mediaflow.ui.theme.currentThemeColor

/**
 * 气泡菜单的展示状态：与 [ComposePopupMenu] 分离，便于用 remember 复用菜单描述，
 * 而开关状态随组合生命周期管理。
 */

/** 气泡阴影高度：保证在白色背景上也能看出边界。 */
private val MenuShadowElevation = 8.dp

@Stable
class ComposePopupMenuState internal constructor(
    internal val menu: ComposePopupMenu
) {

    var isOpen by mutableStateOf(false)
        private set

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
 * 菜单坐标直接取 [content] 自身的窗口坐标（[onGloballyPositioned]），
 * 因此弹出位置始终跟着被点击的那个元素，不会被别处的布局带偏。
 */
@Composable
fun ComposePopupMenuAnchor(
    state: ComposePopupMenuState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var anchorBounds by remember { mutableStateOf(IntRect.Zero) }
    // 承载进出场动画：关闭后仍保留到退场动画播完再移除 Popup
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = state.isOpen
    Box(modifier = modifier) {
        // 外层 Box 可能被父级（例如瀑布流的整行 item）撑满，因此再套一层只包裹内容的 Box，
        // 取它（真实内容）的窗口坐标作为菜单锚点，保证气泡贴着被点击的元素。
        Box(
            modifier = Modifier.onGloballyPositioned { coordinates ->
                anchorBounds = coordinates.boundsInWindow().roundToIntRect()
            }
        ) {
            content()
            if (visibleState.currentState || visibleState.targetState) {
                ComposePopupMenuWindow(
                    state = state,
                    anchorBounds = anchorBounds,
                    visibleState = visibleState
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ComposePopupMenuWindow(
    state: ComposePopupMenuState,
    anchorBounds: IntRect,
    visibleState: MutableTransitionState<Boolean>
) {
    val menu = state.menu
    // 以锚点的一角为缩放原点：END 对齐时从右上角展开，START 对齐时从左上角展开
    val transformOrigin = remember(menu.gravity) {
        if (menu.gravity == Gravity.END) {
            TransformOrigin(1F, 0F)
        } else {
            TransformOrigin(0F, 0F)
        }
    }
    Popup(
        popupPositionProvider = remember(menu, anchorBounds) {
            PopupMenuPositionProvider(anchorBounds, menu.gravity, menu.offsetX, menu.offsetY)
        },
        onDismissRequest = { state.dismiss() },
        properties = PopupProperties(focusable = true)
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(animationSpec = tween(150)) +
                scaleIn(animationSpec = tween(150), initialScale = 0.92F, transformOrigin = transformOrigin),
            exit = fadeOut(animationSpec = tween(100)) +
                scaleOut(animationSpec = tween(100), targetScale = 0.92F, transformOrigin = transformOrigin)
        ) {
            val themeColor = currentThemeColor()
            // 在组合期求值，因此过滤条件可以观察隐私开关等状态
            val visibleItems = menu.items.filter { item ->
                menu.filter?.invoke(item) ?: true
            }
            // 容器对齐 View 版 menu_popup_background：16dp 圆角 + button_background 纯色 + 上下 8dp 内边距
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = themeColor.buttonBackground,
                // 深色底或白色背景上都需要靠阴影划出边界
                shadowElevation = MenuShadowElevation
            ) {
                // IntrinsicSize.Max 让气泡宽度等于最宽条目，而不是被 Popup 的窗口约束撑满
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
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

@OptIn(ExperimentalComposeUiApi::class)
private class PopupMenuPositionProvider(
    private val menuAnchorBounds: IntRect,
    private val gravity: Int,
    private val offsetX: Int,
    private val offsetY: Int
) : PopupPositionProvider {

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val startX = if (gravity == Gravity.END) {
            menuAnchorBounds.right - popupContentSize.width
        } else {
            menuAnchorBounds.left
        }
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(
            (startX + offsetX).coerceIn(0, maxX),
            (menuAnchorBounds.bottom + offsetY).coerceIn(0, maxY)
        )
    }

}
