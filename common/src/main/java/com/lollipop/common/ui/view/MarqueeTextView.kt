package com.lollipop.common.ui.view

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView

class MarqueeTextView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null
) : AppCompatTextView(context, attributeSet) {

    private var mode: Mode = Mode.NORMAL

    override fun isFocused(): Boolean {
        if (mode == Mode.MARQUEE) {
            return true
        }
        return super.isFocused()
    }

    fun marqueeMode() {
        mode = Mode.MARQUEE
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

    fun normalMode(maxLinesCount: Int = 2) {
        mode = Mode.NORMAL
        ellipsize = TextUtils.TruncateAt.END
        isSingleLine = false
        isFocusableInTouchMode = false
        maxLines = maxLinesCount
    }

    private enum class Mode {
        NORMAL,
        MARQUEE
    }

}