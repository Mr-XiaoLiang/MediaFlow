package com.lollipop.mediaflow.ui.view

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView

class MarqueeTextView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null
) : AppCompatTextView(context, attributeSet) {

    init {
        ellipsize = TextUtils.TruncateAt.MARQUEE
        marqueeRepeatLimit = -1
        isSingleLine = true
        isSelected = true
        isFocusable = true
        isFocusableInTouchMode = true
        maxLines = 1
        // 开启横向渐变边缘
        isHorizontalFadingEdgeEnabled = true
        // 设置渐变长度（px），这里可以根据屏幕密度转换 dp
        this.setFadingEdgeLength(
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                40f,
                context.resources.displayMetrics
            ).toInt()
        )
    }

    override fun isFocused(): Boolean {
        return true
    }

}