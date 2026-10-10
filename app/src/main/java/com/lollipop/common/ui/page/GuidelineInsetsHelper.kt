package com.lollipop.common.ui.page

import android.content.Context
import android.util.TypedValue
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.Insets
import androidx.core.view.updateLayoutParams
import com.lollipop.common.tools.LLog.Companion.registerLog

class GuidelineInsetsHelper {

    companion object {

        private val log by lazy {
            registerLog()
        }

        fun update(
            edgeSize: EdgeSize,
            leftGuideline: View?,
            topGuideline: View?,
            rightGuideline: View?,
            bottomGuideline: View?,
        ) {
            try {
                leftGuideline?.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    guideBegin = edgeSize.left
                }
            } catch (e: Throwable) {
                log.e("updateGuidelineInsets: left", e)
            }
            try {
                topGuideline?.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    guideBegin = edgeSize.top
                }
            } catch (e: Throwable) {
                log.e("updateGuidelineInsets: top", e)
            }
            try {
                rightGuideline?.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    guideEnd = edgeSize.right
                }
            } catch (e: Throwable) {
                log.e("updateGuidelineInsets: right", e)
            }
            try {
                bottomGuideline?.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    guideEnd = edgeSize.bottom
                }
            } catch (e: Throwable) {
                log.e("updateGuidelineInsets: bottom", e)
            }
        }
    }

    private val log by lazy {
        registerLog()
    }

    private var leftGuideline: View? = null
    private var topGuideline: View? = null
    private var rightGuideline: View? = null
    private var bottomGuideline: View? = null
    private val minEdgeImpl = EdgeSizeImpl()
    private val currentImpl = EdgeSizeImpl()
    val minEdge: EdgeSize
        get() {
            return minEdgeImpl
        }
    val current: EdgeSize
        get() {
            return currentImpl
        }

    private var isMinEdgeSet = false

    private var callback: OnGuidelineInsetsChangedCallback? = null

    private var filter: OnGuidelineInsetsFilter? = null

    fun onFilter(filter: OnGuidelineInsetsFilter?) {
        this.filter = filter
    }

    fun onChanged(callback: OnGuidelineInsetsChangedCallback?) {
        this.callback = callback
    }

    private fun dp2px(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    fun setMinEdge(context: Context, leftDp: Float, topDp: Float, rightDp: Float, bottomDp: Float) {
        isMinEdgeSet = true
        minEdgeImpl.top = dp2px(context, topDp)
        minEdgeImpl.left = dp2px(context, leftDp)
        minEdgeImpl.right = dp2px(context, rightDp)
        minEdgeImpl.bottom = dp2px(context, bottomDp)
    }

    fun setMinEdge(context: Context, hDp: Float, vDp: Float) {
        val hPx = dp2px(context, hDp)
        val vPx = dp2px(context, vDp)
        isMinEdgeSet = true
        minEdgeImpl.top = vPx
        minEdgeImpl.left = hPx
        minEdgeImpl.right = hPx
        minEdgeImpl.bottom = vPx
    }

    fun setMinEdge(context: Context, edgeDp: Float) {
        val size = dp2px(context, edgeDp)
        isMinEdgeSet = true
        minEdgeImpl.top = size
        minEdgeImpl.left = size
        minEdgeImpl.right = size
        minEdgeImpl.bottom = size
    }

    fun bindGuidelineInsets(
        leftGuideline: View? = null,
        topGuideline: View? = null,
        rightGuideline: View? = null,
        bottomGuideline: View? = null,
    ) {
        this.leftGuideline = leftGuideline
        this.topGuideline = topGuideline
        this.rightGuideline = rightGuideline
        this.bottomGuideline = bottomGuideline
        if (!isMinEdgeSet) {
            arrayOf(
                leftGuideline,
                topGuideline,
                rightGuideline,
                bottomGuideline
            ).find { it != null }?.let {
                setMinEdge(it.context, 16F)
            }
        }
    }

    fun dispatch(edgeSize: EdgeSize) {
        updateGuidelineInsets(edgeSize.left, edgeSize.top, edgeSize.right, edgeSize.bottom)
    }

    fun updateGuidelineInsets(
        left: Int, top: Int, right: Int, bottom: Int
    ) {
        log.i("updateGuidelineInsets: $left, $top, $right, $bottom")
        val guidelineInsets = filterGuidelineInsets(
            SimpleEdgeSize(
                maxOf(left, minEdge.left),
                maxOf(top, minEdge.top),
                maxOf(right, minEdge.right),
                maxOf(bottom, minEdge.bottom)
            )
        )
        currentImpl.left = guidelineInsets.left
        currentImpl.top = guidelineInsets.top
        currentImpl.right = guidelineInsets.right
        currentImpl.bottom = guidelineInsets.bottom

        onGuidelineInsetsChanged(currentImpl)
        update(current, leftGuideline, topGuideline, rightGuideline, bottomGuideline)
    }

    private fun onGuidelineInsetsChanged(
        edgeSize: EdgeSize
    ) {
        callback?.onGuidelineInsetsChanged(edgeSize)
    }

    private fun filterGuidelineInsets(edgeSize: EdgeSize): EdgeSize {
        return filter?.filterGuidelineInsets(edgeSize) ?: edgeSize
    }

    fun interface OnGuidelineInsetsChangedCallback {
        fun onGuidelineInsetsChanged(
            edgeSize: EdgeSize
        )
    }

    fun interface OnGuidelineInsetsFilter {
        fun filterGuidelineInsets(edgeSize: EdgeSize): EdgeSize
    }

    interface EdgeSize {
        val left: Int
        val top: Int
        val right: Int
        val bottom: Int
    }

    class SimpleEdgeSize(
        override val left: Int,
        override val top: Int,
        override val right: Int,
        override val bottom: Int
    ): EdgeSize

    private class EdgeSizeImpl : EdgeSize {
        override var left: Int = 0
        override var top: Int = 0
        override var right: Int = 0
        override var bottom: Int = 0
    }

}