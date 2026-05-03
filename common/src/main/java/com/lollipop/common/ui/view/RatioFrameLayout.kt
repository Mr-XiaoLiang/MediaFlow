package com.lollipop.common.ui.view

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.view.isVisible

class RatioFrameLayout @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null
) : FrameLayout(context, attributeSet) {

    private var ratio: Float = 1f
    private var mode: Mode = Mode.WidthFirst

    fun setRatio(width: Int, height: Int, mode: Mode) {
        this.ratio = width.toFloat() / height.toFloat()
        this.mode = mode
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width: Int
        val height: Int
        when (mode) {
            Mode.WidthFirst -> {
                width = MeasureSpec.getSize(widthMeasureSpec)
                height = (width / ratio).toInt()
            }

            Mode.HeightFirst -> {
                height = MeasureSpec.getSize(heightMeasureSpec)
                width = (height * ratio).toInt()
            }
        }
        val count = childCount
        val childWidth = width - paddingLeft - paddingRight
        val childHeight = height - paddingTop - paddingBottom
        val widthSpec = MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY)
        val heightSpec = MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY)
        for (i in 0 until count) {
            val child = getChildAt(i)
            if (!child.isVisible) {
                continue
            }
            child.measure(widthSpec, heightSpec)
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width: Int
        val height: Int
        when (mode) {
            Mode.WidthFirst -> {
                width = right - left
                height = (width / ratio).toInt()
            }

            Mode.HeightFirst -> {
                height = bottom - top
                width = (height * ratio).toInt()
            }
        }
        val childLeft = paddingLeft
        val childTop = paddingTop
        val childWidth = width - childLeft - paddingRight
        val childHeight = height - childTop - paddingBottom
        val count = childCount
        for (i in 0 until count) {
            val child = getChildAt(i)
            if (!child.isVisible) {
                continue
            }
            child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight)
        }
    }

    enum class Mode {
        WidthFirst,
        HeightFirst
    }

}