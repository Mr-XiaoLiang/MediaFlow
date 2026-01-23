package com.lollipop.mediaflow.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import androidx.core.content.withStyledAttributes
import com.lollipop.mediaflow.R

class TabGroup @JvmOverloads constructor(
    context: Context, attr: AttributeSet? = null
) : LinearLayout(context, attr) {

    companion object {
        const val SELECTED_NONE = TabBackgroundDrawable.SELECTED_NONE
    }

    private val tabBackgroundDrawable = TabBackgroundDrawable()

    var tabBackgroundColor: Int
        get() = tabBackgroundDrawable.background
        set(value) {
            tabBackgroundDrawable.background = value
        }

    var tabColor: Int
        get() = tabBackgroundDrawable.color
        set(value) {
            tabBackgroundDrawable.color = value
        }

    private var lastTab = SELECTED_NONE
    private var currentTab = SELECTED_NONE

    private val animator by lazy {
        ValueAnimator().apply {
            interpolator = LinearInterpolator()
            duration = 300
            setFloatValues(0F, 1F)
            addUpdateListener {
                val value = it.animatedValue
                if (value is Float) {
                    onAnimationUpdate(value)
                }
            }
        }
    }

    private val tabRangeProvider = object : TabBackgroundDrawable.TabRangeProvider {
        override fun getTabRange(index: Int, outRange: Rect): Boolean {
            if (index !in 0..<childCount) {
                return false
            }
            val child = getChildAt(index)
            outRange.set(child.left, child.top, child.right, child.bottom)
            return true
        }

        override fun getTabCount(): Int {
            return childCount
        }
    }

    init {
        val oldBg = background
        if (oldBg is ColorDrawable) {
            tabBackgroundColor = oldBg.color
        }
        background = tabBackgroundDrawable
        tabBackgroundDrawable.bindRangeProvider(tabRangeProvider)
        orientation = HORIZONTAL
        attr?.let {
            context.withStyledAttributes(it, R.styleable.TabGroup) {
                tabColor = getColor(R.styleable.TabGroup_tabColor, Color.TRANSPARENT)
            }
        }
        tabBackgroundDrawable.setOverflow(paddingLeft, paddingTop, paddingRight, paddingBottom)
        if (isInEditMode) {
            select(0)
        }
    }

    fun select(index: Int) {
        if (currentTab == index) {
            return
        }
        lastTab = currentTab
        currentTab = index
        if (lastTab != SELECTED_NONE) {
            animator.cancel()
            animator.start()
        } else {
            lastTab = index
            onAnimationUpdate(0F)
        }
    }

    private fun onAnimationUpdate(progress: Float) {
        tabBackgroundDrawable.select(lastTab, currentTab, progress)
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        super.setPadding(left, top, right, bottom)
        tabBackgroundDrawable.setOverflow(left, top, right, bottom)
    }

}