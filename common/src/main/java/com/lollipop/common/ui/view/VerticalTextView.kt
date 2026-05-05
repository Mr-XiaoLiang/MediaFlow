package com.lollipop.common.ui.view

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.withSave
import com.lollipop.common.R

class VerticalTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {

    // 默认逆时针 90 度（文字向上），杂志书脊常用
    var isClockwise = true

    init {
        attrs?.let {
            context.withStyledAttributes(it, R.styleable.VerticalTextView) {
                isClockwise = getBoolean(R.styleable.VerticalTextView_isClockwise, true)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 1. 交换输入参数进行测量
        super.onMeasure(heightMeasureSpec, widthMeasureSpec)
        // 2. 交换测量结果，传回给父布局
        setMeasuredDimension(measuredHeight, measuredWidth)
    }

    override fun onDraw(canvas: Canvas) {
        val paint = paint
        paint.color = currentTextColor

        canvas.withSave {
            if (isClockwise) {
                // 顺时针旋转 90 度
                translate(width.toFloat(), 0f)
                rotate(90f)
            } else {
                // 逆时针旋转 90 度
                translate(0f, height.toFloat())
                rotate(-90f)
            }
            super.onDraw(canvas)
            layout.draw(this)
        }
    }
}
