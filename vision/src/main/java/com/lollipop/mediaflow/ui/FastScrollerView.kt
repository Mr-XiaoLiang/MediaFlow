package com.lollipop.mediaflow.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.withStyledAttributes
import com.lollipop.common.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FastScrollerView(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    companion object {
        private const val PROGRESS_MAX = 1F
        private const val PROGRESS_MIN = 0F
    }

    var progress = 0F
        private set

    private val touchBarPaint = Paint().apply {
        isAntiAlias = true
        isDither = true
    }

    private val bounds = Bounds()

    private var touchBarHeight: Int
        get() {
            return bounds.touchBarHeight
        }
        set(value) {
            bounds.updateTouchBarHeight(value)
            bounds.updateBounds()
        }

    private var touchBarColor: Int
        get() {
            return touchBarPaint.color
        }
        set(value) {
            touchBarPaint.color = value
        }

    var onDragListener: OnDragListener? = null

    private val state = State()

    init {
        attrs?.let {
            context.withStyledAttributes(it, R.styleable.FastScrollerView) {
                touchBarHeight = getDimensionPixelSize(
                    R.styleable.FastScrollerView_touchBarHeight, 10
                )
                touchBarColor = getColor(
                    R.styleable.FastScrollerView_touchBarColor, Color.BLACK
                )
            }
        }
        state.init(context)
        if (isInEditMode) {
            updateProgress(0.5F)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateBounds()
    }

    private fun updateBounds() {
        bounds.updatePadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
        bounds.onSizeChange(width, height)
        bounds.updateBounds()
        bounds.update(progress)
    }

    fun updateProgress(progress: Float) {
        if (state.isCapture) {
            // 手指滑动过程中，就不接受外部设置
            return
        }
        onProgressUpdate(progress)
    }

    private fun onProgressUpdate(progress: Float) {
        val finalProgress = min(max(progress, PROGRESS_MIN), PROGRESS_MAX)
        this.progress = finalProgress
        bounds.update(finalProgress)
        onDragListener?.onProgressChange(finalProgress, state.isCapture)
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event?.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                onTouchDown(event.x, event.y)
            }

            MotionEvent.ACTION_MOVE -> {
                onTouchMove(event.x, event.y)
            }

            MotionEvent.ACTION_UP -> {
                onTouchCancel()
            }

            MotionEvent.ACTION_CANCEL -> {
                onTouchCancel()
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                onTouchCancel()
            }
        }
        return super.onTouchEvent(event) || state.touchMode != TouchMode.Cancel
    }

    private fun onTouchDown(x: Float, y: Float) {
        if (y < bounds.touchBarTop || y > bounds.touchBarBottom) {
            onTouchCancel()
            return
        }
        state.touchDown(x, y)
        state.initialProgress = progress
        onDragListener?.onDragStart()
    }

    private fun onTouchMove(x: Float, y: Float) {
        state.currentX = x
        state.currentY = y
        when (state.touchMode) {
            TouchMode.Pending -> {
                if (abs(state.dy) > state.touchSlop) {
                    state.touchMode = TouchMode.Dragging
                }
            }

            TouchMode.Dragging -> {
                val offset = state.dy / bounds.offsetMax
                val newProgress = state.initialProgress + offset
                onProgressUpdate(newProgress)
            }

            TouchMode.Cancel -> {
                return
            }
        }
    }

    private fun onTouchCancel() {
        if (state.touchMode != TouchMode.Cancel) {
            state.touchMode = TouchMode.Cancel
            onDragListener?.onDragEnd()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRoundRect(
            bounds.touchBarLeft,
            bounds.touchBarTop,
            bounds.touchBarRight,
            bounds.touchBarBottom,
            bounds.radius,
            bounds.radius,
            touchBarPaint
        )
    }

    interface OnDragListener {

        fun onDragStart()

        fun onProgressChange(progress: Float, fromUser: Boolean)

        fun onDragEnd()
    }

    private class State {
        var touchSlop = 0
        var initialX = 0f
        var initialY = 0f
        var currentX = 0F
        var currentY = 0F
        var touchMode = TouchMode.Cancel

        var initialProgress = 0F

        val dx: Float
            get() {
                return currentX - initialX
            }

        val dy: Float
            get() {
                return currentY - initialY
            }

        val isCapture: Boolean
            get() {
                return touchMode == TouchMode.Dragging
            }

        fun init(context: Context) {
            touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        }

        fun touchDown(x: Float, y: Float) {
            initialX = x
            initialY = y
            currentX = x
            currentY = y
            touchMode = TouchMode.Pending
        }

    }

    private enum class TouchMode {
        /**
         * 等待响应
         */
        Pending,

        /**
         * 捕获中
         * 单手指
         */
        Dragging,

        /**
         * 被取消
         */
        Cancel;

    }

    private class Bounds {

        var paddingTop = 0
            private set
        var paddingBottom = 0
            private set
        var paddingLeft = 0
            private set
        var paddingRight = 0
            private set

        var touchBarHeight = 0
            private set

        var viewWidth = 0
            private set
        var viewHeight = 0
            private set

        var offsetMax = 0
            private set

        var touchBarTop = 0F
            private set
        var touchBarBottom = 0F
            private set
        var touchBarLeft = 0F
            private set
        var touchBarRight = 0F
            private set

        var radius = 0F
            private set

        fun onSizeChange(viewWidth: Int, viewHeight: Int) {
            this.viewWidth = viewWidth
            this.viewHeight = viewHeight
        }

        fun updatePadding(left: Int, top: Int, right: Int, bottom: Int) {
            paddingLeft = left
            paddingTop = top
            paddingRight = right
            paddingBottom = bottom
        }

        fun updateTouchBarHeight(height: Int) {
            touchBarHeight = height
        }

        fun updateBounds() {
            offsetMax = viewHeight - touchBarHeight - paddingTop - paddingBottom
            touchBarLeft = paddingLeft.toFloat()
            touchBarRight = (viewWidth - paddingRight).toFloat()
            val touchBarWidth = touchBarRight - touchBarLeft
            radius = min(touchBarHeight * 0.5F, touchBarWidth * 0.5F)
        }

        fun update(progress: Float) {
            var offset = (offsetMax * progress).toInt()
            if (offset < 0) {
                offset = 0
            } else if (offset > offsetMax) {
                offset = offsetMax
            }
            touchBarTop = (paddingTop + offset).toFloat()
            touchBarBottom = touchBarTop + touchBarHeight
        }

    }

}