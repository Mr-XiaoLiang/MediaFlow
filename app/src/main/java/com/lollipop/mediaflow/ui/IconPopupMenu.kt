package com.lollipop.mediaflow.ui

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ListAdapter
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.ListPopupWindow
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.databinding.ItemMenuPopBinding
import kotlin.math.max

object IconPopupMenu {

    fun create(anchor: View): Builder {
        return Builder(anchor)
    }

    fun hold(buildBlock: (Builder) -> Unit): MenuHolder {
        return MenuHolder(buildBlock)
    }

    class Option(
        val anchor: View,
        val menuList: List<MenuItemEntity>,
        val gravity: Int = Gravity.END,
        val offsetX: Int = 0,
        val offsetY: Int = 0,
        val clickCallback: (MenuItemEntity) -> Boolean,
        val customBuilder: (ListPopupWindow) -> Unit = {},
    )

    class Builder(val anchor: View) {
        private val menu = mutableListOf<MenuItemEntity>()

        private var gravity: Int = Gravity.END

        private var onClick: (MenuItemEntity) -> Boolean = { false }

        private var customBuilder: (ListPopupWindow) -> Unit = {}

        private var offsetX: Int = 0
        private var offsetY: Int = 0

        fun offset(offsetX: Int, offsetY: Int): Builder {
            this.offsetX = offsetX
            this.offsetY = offsetY
            return this
        }

        fun offsetDp(offsetXDp: Int, offsetYDp: Int): Builder {
            offset(
                offsetX = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    offsetXDp.toFloat(),
                    anchor.resources.displayMetrics
                ).toInt(),
                offsetY = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    offsetYDp.toFloat(),
                    anchor.resources.displayMetrics
                ).toInt(),
            )
            return this
        }

        fun gravity(gravity: Int): Builder {
            this.gravity = gravity
            return this
        }

        fun addMenu(tag: String, @StringRes titleRes: Int, @DrawableRes iconRes: Int): Builder {
            menu.add(MenuItemEntity(tag, titleRes, iconRes))
            return this
        }

        fun onClick(onClick: (MenuItemEntity) -> Boolean): Builder {
            this.onClick = onClick
            return this
        }

        fun custom(builder: (ListPopupWindow) -> Unit): Builder {
            customBuilder = builder
            return this
        }

        fun build(): Option {
            return Option(
                anchor = anchor,
                menuList = menu,
                gravity = gravity,
                offsetX = offsetX,
                offsetY = offsetY,
                clickCallback = onClick,
                customBuilder = customBuilder
            )
        }

        fun show() {
            show(build())
        }

    }

    class MenuHolder(
        val buildBlock: (Builder) -> Unit,
    ) {

        private var popupWindow: ListPopupWindow? = null

        fun show(anchor: View) {
            val pop = popupWindow
            if (pop != null) {
                pop.anchorView = anchor
                pop.show()
                return
            }
            val builder = create(anchor)
            buildBlock.invoke(builder)
            val option = builder.build()
            val newPop = build(option)
            popupWindow = newPop
            newPop.show()
        }

    }

    fun build(
        option: Option
    ): ListPopupWindow {
        val anchor = option.anchor
        val context = anchor.context
        val popupWindow = ListPopupWindow(context)
        popupWindow.anchorView = anchor
        popupWindow.setBackgroundDrawable(
            ContextCompat.getDrawable(
                context,
                R.drawable.menu_popup_background
            )
        )

        val adapter = MenuAdapter(context, option.menuList) {
            if (option.clickCallback(it)) {
                popupWindow.dismiss()
                true
            } else {
                false
            }
        }
        popupWindow.setDropDownGravity(option.gravity)
        popupWindow.setContentWidth(measureContentWidth(context, adapter))
        popupWindow.horizontalOffset = option.offsetX
        popupWindow.setVerticalOffset(option.offsetY)
        popupWindow.setAdapter(adapter)
        popupWindow.setModal(true)
        option.customBuilder.invoke(popupWindow)
        return popupWindow
    }

    fun show(option: Option) {
        build(option).show()
    }

    private fun measureContentWidth(context: Context, adapter: ListAdapter): Int {
        var maxWidth = 0
        var itemView: View? = null
        val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

        val container = FrameLayout(context)
        for (i in 0 until adapter.count) {
            itemView = adapter.getView(i, itemView, container)
            itemView.measure(widthMeasureSpec, heightMeasureSpec)
            maxWidth = max(maxWidth, itemView.measuredWidth)
        }
        return maxWidth
    }


    class MenuItemEntity(
        val tag: String,
        @param:StringRes
        val titleRes: Int,
        @param:DrawableRes
        val iconRes: Int
    )

    private class MenuAdapter(
        private val context: Context,
        private val menu: List<MenuItemEntity>,
        private val onClick: (MenuItemEntity) -> Boolean
    ) : BaseAdapter() {

        private var layoutInflater = LayoutInflater.from(context)

        override fun getCount(): Int {
            return menu.size
        }

        override fun getItem(position: Int): Any {
            return menu[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getView(
            position: Int,
            convertView: View?,
            parent: ViewGroup
        ): View? {
            if (position >= menu.size || position < 0) {
                return null
            }
            val binding = if (convertView != null) {
                ItemMenuPopBinding.bind(convertView)
            } else {
                createView(parent)
            }
            bindView(binding, position)
            return binding.root
        }

        private fun createView(parent: ViewGroup): ItemMenuPopBinding {
            return ItemMenuPopBinding.inflate(layoutInflater, parent, false)
        }

        private fun bindView(binding: ItemMenuPopBinding, position: Int) {
            val item = menu[position]
            binding.titleView.setText(item.titleRes)
            binding.iconView.setImageResource(item.iconRes)
            binding.iconView.isVisible = item.iconRes != 0
            binding.root.setOnClickListener(ClickWrapper(item, onClick))
        }

    }

    private class ClickWrapper(
        private val info: MenuItemEntity,
        private val onClick: (MenuItemEntity) -> Boolean
    ) : View.OnClickListener {
        override fun onClick(v: View?) {
            onClick(info)
        }
    }

}