package com.lollipop.auditory.player

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.lollipop.auditory.R
import com.lollipop.common.ui.view.OutlineTextView
import com.lollipop.common.ui.view.TagTextView

sealed class AudioFlag(val value: String) {

    abstract fun createView(context: Context, style: ViewStyle): TextView

    protected fun updateFontStyle(textView: TextView, style: ViewStyle) {
        // 1. 从 res/font 获取字体家族
        val family = ResourcesCompat.getFont(textView.context, R.font.roboto_variable_font_wght)
        textView.typeface = Typeface.create(family, 500, false)
        textView.textSize = style.textSize
        val paddingHorizontal = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            style.paddingHorizontal,
            textView.context.resources.displayMetrics
        ).toInt()
        val paddingVertical = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            style.paddingVertical,
            textView.context.resources.displayMetrics
        ).toInt()
        textView.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)
        textView.layoutParams = ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).also {
            it.topMargin = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                2F,
                textView.context.resources.displayMetrics
            ).toInt()
            it.marginEnd = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                6F,
                textView.context.resources.displayMetrics
            ).toInt()
        }
    }

    class Excellent(value: String) : AudioFlag(value) {

        override fun createView(
            context: Context,
            style: ViewStyle
        ): TextView {
            return TagTextView(context).also {
                updateFontStyle(it, style)
                it.color = style.color
                it.text = value
                it.radius = style.radius
            }
        }
    }

    class Good(value: String) : AudioFlag(value) {
        override fun createView(
            context: Context,
            style: ViewStyle
        ): TextView {
            return OutlineTextView(context).also {
                updateFontStyle(it, style)
                it.color = style.color
                it.text = value
                it.radius = style.radius
                it.borderWidth = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    style.borderWidth,
                    context.resources.displayMetrics
                )
            }
        }

    }

    class Ordinary(value: String) : AudioFlag(value) {
        override fun createView(
            context: Context,
            style: ViewStyle
        ): TextView {
            return TextView(context).also {
                updateFontStyle(it, style)
                it.text = value
                it.setTextColor(style.color)
            }
        }
    }

    class ViewStyle(
        val color: Int,
        val radius: Float = 0F,
        val borderWidth: Float = 0.5F,
        val paddingHorizontal: Float = 4F,
        val paddingVertical: Float = 0F,
        val textSize: Float = 11F,
    )

}