package com.lollipop.mediaflow.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.withStyledAttributes
import com.lollipop.mediaflow.R

class TagTextView @JvmOverloads constructor(
    context: Context, attributeSet: AttributeSet? = null
) : AppCompatTextView(context, attributeSet) {

    var radius: Float = 0F
        set(value) {
            field = value
            invalidate()
        }

    var color: Int
        get() {
            return backgroundPaint.color
        }
        set(value) {
            backgroundPaint.color = value
            invalidate()
        }

    private val backgroundBounds = RectF()

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)

    init {
        attributeSet?.let {
            context.withStyledAttributes(it, R.styleable.TagTextView) {
                color = getColor(R.styleable.TagTextView_android_color, Color.WHITE)
                radius = getDimension(R.styleable.TagTextView_android_radius, 0F)
            }
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        backgroundBounds.set(
            0F,
            0F,
            (right - left).toFloat(),
            (bottom - top).toFloat()
        )
    }

    override fun onDraw(canvas: Canvas) {
        // 1. 开启离屏缓冲
        val count = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)

        // 2. 先画底块（白色背景）
        canvas.drawRoundRect(backgroundBounds, radius, radius, backgroundPaint)

        // 4. 将 TextView 默认的文字 Paint 临时改为镂空模式
        val superPaint = getPaint()
        val originalMode = superPaint.xfermode
        superPaint.xfermode = xfermode

        // 5. 调用父类绘制逻辑（这步会处理 Padding, TextSize, Gravity 等一切）
        super.onDraw(canvas)

        // 6. 还原状态
        superPaint.xfermode = originalMode
        canvas.restoreToCount(count)
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