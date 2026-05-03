package com.lollipop.common.ui.view

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.widget.FrameLayout

class GestureExclusionFrameLayout(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private val exclusionRect = Rect()
    private val exclusionRectList = listOf(exclusionRect)

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            exclusionRect.set(0, 0, width, height)
            // 设置排除区域
            systemGestureExclusionRects = exclusionRectList
        }
    }
}