package com.lollipop.mediaflow.view

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

class HalfSpace @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null
) : View(context, attributeSet) {


    private val ratio: Float = 2f
    private var mode: Mode = Mode.WidthFirst

    fun setMode(mode: Mode) {
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
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        // 留空，不绘制
    }

    enum class Mode {
        WidthFirst,
        HeightFirst
    }


}