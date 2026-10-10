package com.lollipop.common.ui.view

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

class SizeObserverFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var onSizeChangedListener: OnSizeChangedListener? = null

    fun onSizeChanged(callback: OnSizeChangedListener?) {
        onSizeChangedListener = callback
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        onSizeChangedListener?.onSizeChanged(right - left, bottom - top)
    }

    fun interface OnSizeChangedListener {
        fun onSizeChanged(width: Int, height: Int)
    }

}