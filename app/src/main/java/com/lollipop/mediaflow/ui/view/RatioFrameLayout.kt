package com.lollipop.mediaflow.ui.view

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.view.isVisible

class RatioFrameLayout @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null
) : FrameLayout(context, attributeSet) {

    private var ratio: Float = 1f

    fun setRatio(width: Int, height: Int) {
        ratio = width.toFloat() / height.toFloat()
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (width / ratio).toInt()
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
        val width = right - left
        val height = (width / ratio).toInt()
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
            child.layout(childLeft, childTop, childWidth, childHeight)
        }
    }

}