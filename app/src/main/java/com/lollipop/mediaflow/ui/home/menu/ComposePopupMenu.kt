package com.lollipop.mediaflow.ui.home.menu

import android.view.Gravity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * Compose 版的气泡菜单（对标 View 版 [com.lollipop.common.ui.view.IconPopupMenu]）。
 *
 * 只负责「描述菜单」：条目、过滤、点击回调、弹出方位与偏移，全部通过 Builder 声明，
 * 具体展示由 [ComposePopupMenuAnchor] 在 Compose 中完成，保持与 View 版一致的扩展性。
 */
class ComposePopupMenu private constructor(
    internal val items: List<Item>,
    internal val gravity: Int,
    internal val offsetX: Int,
    internal val offsetY: Int,
    internal val filter: ((Item) -> Boolean)?,
    internal val onClick: (Item) -> Unit
) {

    class Item(
        val tag: String,
        @param:StringRes val titleRes: Int,
        @param:DrawableRes val iconRes: Int
    )

    class Builder internal constructor(
        private val density: Density
    ) {

        private val menu = mutableListOf<Item>()
        private var gravity = Gravity.END
        private var offsetX = 0
        private var offsetY = 0
        private var filter: ((Item) -> Boolean)? = null
        private var onClick: (Item) -> Unit = {}

        fun addMenu(
            tag: String,
            @StringRes titleRes: Int,
            @DrawableRes iconRes: Int
        ): Builder = apply {
            menu.add(Item(tag, titleRes, iconRes))
        }

        fun gravity(gravity: Int): Builder = apply {
            this.gravity = gravity
        }

        /** 以像素指定偏移（对齐 View 版 IconPopupMenu.Builder.offset）。 */
        fun offset(offsetX: Int, offsetY: Int): Builder = apply {
            this.offsetX = offsetX
            this.offsetY = offsetY
        }

        /** 以 dp 指定偏移（对齐 View 版 IconPopupMenu.Builder.offsetDp）。 */
        fun offsetDp(offsetXDp: Int, offsetYDp: Int): Builder = apply {
            offset(
                offsetX = with(density) { offsetXDp.dp.roundToPx() },
                offsetY = with(density) { offsetYDp.dp.roundToPx() }
            )
        }

        /** 过滤条件在展示时求值，因此可以读取外部状态（隐私开关、当前页类型等）。 */
        fun filter(filter: ((Item) -> Boolean)?): Builder = apply {
            this.filter = filter
        }

        fun onClick(onClick: (Item) -> Unit): Builder = apply {
            this.onClick = onClick
        }

        fun build(): ComposePopupMenu {
            return ComposePopupMenu(
                items = menu.toList(),
                gravity = gravity,
                offsetX = offsetX,
                offsetY = offsetY,
                filter = filter,
                onClick = onClick
            )
        }

    }

}
