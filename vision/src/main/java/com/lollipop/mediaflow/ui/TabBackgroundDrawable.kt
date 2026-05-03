package com.lollipop.mediaflow.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import kotlin.math.max
import kotlin.math.min

class TabBackgroundDrawable : Drawable() {

    companion object {
        const val SELECTED_NONE = -1
    }

    private var rangeProvider: TabRangeProvider? = null
    private var fromIndex = SELECTED_NONE
    private var toIndex = SELECTED_NONE
    private var progress = 0F
    var radiusWeight = 1F

    private val tabPath = Path()
    private val backgroundPath = Path()

    private val overflow = Rect()

    private val fromBounds = Rect()
    private val toBounds = Rect()

    private val backgroundPaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.FILL
        color = Color.TRANSPARENT
    }

    private val tabPaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.FILL
    }

    private val startInterpolator by lazy { DecelerateInterpolator(1.5F) }
    private val endInterpolator by lazy { AccelerateInterpolator(1.5F) }

    var background: Int
        get() {
            return backgroundPaint.color
        }
        set(value) {
            backgroundPaint.color = value
        }

    var color: Int
        get() {
            return tabPaint.color
        }
        set(value) {
            tabPaint.color = value
        }

    /**
     * 设置每个Tab的范围
     */
    fun bindRangeProvider(rangeProvider: TabRangeProvider) {
        this.rangeProvider = rangeProvider
        select(fromTab = fromIndex, toTab = toIndex, progress = progress)
    }

    /**
     * 设置每个Tab的溢出参数
     */
    fun setOverflow(left: Int, top: Int, right: Int, bottom: Int) {
        this.overflow.set(left, top, right, bottom)
    }

    /**
     * 选择一个Tab
     */
    fun select(fromTab: Int, toTab: Int, progress: Float) {
        fromIndex = fromTab
        toIndex = toTab
        this.progress = progress
        val count = rangeProvider?.getTabCount() ?: 0
        val itemRange = 0..<count
        if (fromIndex !in itemRange) {
            fromIndex = SELECTED_NONE
        }
        if (toIndex !in itemRange) {
            toIndex = SELECTED_NONE
        }
        updatePath()
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        updatePath()
        backgroundPath.reset()
        buildPath(
            backgroundPath,
            bounds.left.toFloat(),
            bounds.top.toFloat(),
            bounds.right.toFloat(),
            bounds.bottom.toFloat()
        )
    }

    private fun updatePath() {
        tabPath.reset()
        val provider = rangeProvider ?: return
        val tabCount = provider.getTabCount()
        if (tabCount == 0) {
            return
        }
        if (fromIndex == SELECTED_NONE && toIndex == SELECTED_NONE) {
            return
        }
        if (fromIndex == SELECTED_NONE) {
            fromIndex = toIndex
        } else if (toIndex == SELECTED_NONE) {
            toIndex = fromIndex
        }
        fromBounds.set(0, 0, 0, 0)
        toBounds.set(0, 0, 0, 0)
        if (fromIndex == toIndex) {
            val enable = provider.getTabRange(fromIndex, fromBounds)
            if (enable && !fromBounds.isEmpty) {
                buildPath(fromBounds)
            }
        } else {
            val fromEnable = provider.getTabRange(fromIndex, fromBounds)
            val toEnable = provider.getTabRange(toIndex, toBounds)
            if (!fromEnable) {
                fromBounds.set(0, 0, 0, 0)
            }
            if (!toEnable) {
                toBounds.set(0, 0, 0, 0)
            }
            if (fromBounds.isEmpty && toBounds.isEmpty) {
                return
            }
            if (fromBounds.isEmpty) {
                buildPath(toBounds)
            } else if (toBounds.isEmpty) {
                buildPath(fromBounds)
            } else {
                val startProgress = startInterpolator.getInterpolation(progress)
                val endProgress = endInterpolator.getInterpolation(progress)
                val leftProgress = if (fromIndex > toIndex) {
                    startProgress
                } else {
                    endProgress
                }
                val rightProgress = if (fromIndex > toIndex) {
                    endProgress
                } else {
                    startProgress
                }
                val left =
                    (toBounds.left - fromBounds.left) * leftProgress + fromBounds.left - overflow.left
                val top = (toBounds.top - fromBounds.top) * progress + fromBounds.top - overflow.top
                val right =
                    (toBounds.right - fromBounds.right) * rightProgress + fromBounds.right + overflow.right
                val bottom =
                    (toBounds.bottom - fromBounds.bottom) * progress + fromBounds.bottom + overflow.bottom
                buildPath(tabPath, left, top, right, bottom)
            }
        }
    }

    private fun buildPath(range: Rect) {
        buildPath(
            tabPath,
            (range.left - overflow.left).toFloat(),
            (range.top - overflow.top).toFloat(),
            (range.right + overflow.right).toFloat(),
            (range.bottom + overflow.bottom).toFloat()
        )
    }

    private fun buildPath(outPath: Path, left: Float, top: Float, right: Float, bottom: Float) {
        outPath.reset()
        val width = right - left
        val height = bottom - top
        val rw = max(0F, min(1F, radiusWeight))
        val radius = min(height, width) * rw / 2
        outPath.addRoundRect(
            left, top, right, bottom, radius, radius, Path.Direction.CW
        )
    }

    override fun draw(canvas: Canvas) {
        if (background != Color.TRANSPARENT) {
            canvas.drawPath(backgroundPath, backgroundPaint)
        }
        if (tabPath.isEmpty) {
            return
        }
        canvas.drawPath(tabPath, tabPaint)
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int {
        return PixelFormat.TRANSPARENT
    }

    override fun setAlpha(alpha: Int) {
        tabPaint.alpha = alpha
        backgroundPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        tabPaint.setColorFilter(colorFilter)
        backgroundPaint.setColorFilter(colorFilter)
    }

    interface TabRangeProvider {
        fun getTabRange(index: Int, outRange: Rect): Boolean
        fun getTabCount(): Int
    }

}