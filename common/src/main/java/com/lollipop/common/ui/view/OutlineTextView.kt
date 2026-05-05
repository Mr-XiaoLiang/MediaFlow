package com.lollipop.common.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.withStyledAttributes
import com.lollipop.common.R

open class OutlineTextView @JvmOverloads constructor(
    context: Context, attributeSet: AttributeSet? = null
) : AppCompatTextView(context, attributeSet) {

    var radius: Float = 0F
        set(value) {
            field = value
            invalidate()
        }

    var color: Int
        get() {
            return borderPaint.color
        }
        set(value) {
            borderPaint.color = value
            setTextColor(value)
            invalidate()
        }

    private val borderPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
    }

    var borderWidth: Float
        set(value) {
            paint.strokeWidth = value
            buildOutlineBounds()
            invalidate()
        }
        get() {
            return paint.strokeWidth
        }

    private val outlineBounds = RectF()

    init {
        attributeSet?.let {
            context.withStyledAttributes(it, R.styleable.OutlineTextView) {
                color = getColor(R.styleable.OutlineTextView_android_color, Color.WHITE)
                radius = getDimension(R.styleable.OutlineTextView_android_radius, 0F)
                borderWidth = getDimension(R.styleable.OutlineTextView_borderWidth, 0F)
            }
        }
    }

    private fun buildOutlineBounds() {
        val borderHalf = this.borderWidth
        outlineBounds.set(
            borderHalf,
            borderHalf,
            (right - left - borderHalf),
            (bottom - top - borderHalf)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        buildOutlineBounds()

    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRoundRect(outlineBounds, radius, radius, borderPaint)
    }

    override fun setBackground(background: Drawable?) {
    }

    override fun setBackgroundDrawable(background: Drawable?) {
    }

    override fun setBackgroundColor(color: Int) {
    }

    override fun setBackgroundResource(resid: Int) {
    }
}