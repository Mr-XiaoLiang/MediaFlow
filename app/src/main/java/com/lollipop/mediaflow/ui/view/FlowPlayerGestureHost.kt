package com.lollipop.mediaflow.ui.view

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import com.lollipop.mediaflow.tools.task
import kotlin.math.absoluteValue

/**
 * 此处我们固定只处理纵向滚动的ViewPager2下，横向的手势分发
 */
class FlowPlayerGestureHost @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var touchSlop = 0
    private var longPressTimeout = 0L
    private var initialX = 0f
    private var initialY = 0f
    private var currentX = 0F
    private var currentY = 0F
    private var touchState = TouchState.Pending

    private val longPressTask = task {
        onTimeOut()
    }

    var flowTouchListener: OnFlowTouchListener? = null

    private val penetrateViewList = mutableListOf<View>()
    private val tempViewBounds = Rect()

    init {
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        // 获取系统默认长按时间并适当减小，以提升视频操作的爽快感，结果大约在 350ms 左右
        longPressTimeout = (ViewConfiguration.getLongPressTimeout() * 0.7).toLong()
    }

    fun registerPenetrate(view: View) {
        penetrateViewList.add(view)
    }

    fun unregisterPenetrate(view: View) {
        penetrateViewList.remove(view)
    }

    private fun isPenetrate(x: Float, y: Float): Boolean {
        if (penetrateViewList.isEmpty()) {
            return false
        }
        val xInt = x.toInt()
        val yInt = y.toInt()
        for (view in penetrateViewList) {
            getChildRect(view, tempViewBounds)
            if (tempViewBounds.contains(xInt, yInt)) {
                return true
            }
        }
        return false
    }

    private fun getChildRect(view: View, out: Rect) {
        val viewWidth = view.width
        val viewHeight = view.height
        var viewLeft = view.left
        var viewTop = view.top
        var target = view
        while (true) {
            val viewParent = target.parent
            if (viewParent === this) {
                // 要么找到本体返回
                out.set(viewLeft, viewTop, viewLeft + viewWidth, viewTop + viewHeight)
                return
            }
            if (viewParent is View) {
                target = viewParent
                viewLeft -= target.left
                viewTop -= target.top
            } else {
                // 否则找到头不是自己的Child，返回空的
                out.set(0, 0, 0, 0)
                return
            }
        }
    }

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        // 没有child就放弃拦截
        if (childCount < 1 || flowTouchListener == null) {
            return super.dispatchTouchEvent(e)
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = e.x
                initialY = e.y
                // 如果属于穿透区域，那么就放弃事件
                if (isPenetrate(initialX, initialY)) {
                    longPressTask.cancel()
                    return super.dispatchTouchEvent(e)
                }
                currentX = initialX
                currentY = initialY
                touchState = TouchState.Pending
                parent.requestDisallowInterceptTouchEvent(true)
                longPressTask.cancel()
                longPressTask.delayOnUI(longPressTimeout)
            }

            MotionEvent.ACTION_MOVE -> {
                onTouchMove(e.x, e.y)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // 只处理一个指头的情况
                cancelTouch()
            }

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                // 结束了
                cancelTouch()
            }

            else -> {

            }
        }
        return super.dispatchTouchEvent(e) || touchState == TouchState.Capture
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        // 一旦进入 Capture 状态，立刻拦截，不再让子 View 收到任何事件
        if (touchState == TouchState.Capture) {
            return true
        }
        return super.onInterceptTouchEvent(e)
    }

    private fun onTouchMove(x: Float, y: Float) {
        currentX = x
        currentY = y
        when (touchState) {
            TouchState.Pending -> {
                val dx = currentX - initialX
                val dy = currentY - initialY
                if (dx.absoluteValue > touchSlop * 2) {
                    touchCapture()
                } else if (dy.absoluteValue > touchSlop) {
                    cancelTouch()
                }
            }

            TouchState.Capture -> {
                parent.requestDisallowInterceptTouchEvent(true)
                flowTouchListener?.onTouchMove(
                    viewWidth = width,
                    viewHeight = height,
                    touchDownX = initialX,
                    touchDownY = initialY,
                    currentX = currentX,
                    currentY = currentY
                )
            }

            TouchState.Cancel -> {
                // 不做任何事
            }
        }
    }

    private fun cancelTouch() {
        if (touchState == TouchState.Capture) {
            flowTouchListener?.onTouchRelease()
        }
        touchState = TouchState.Cancel
        longPressTask.cancel()
        parent.requestDisallowInterceptTouchEvent(false)
    }

    private fun touchCapture() {
        touchState = TouchState.Capture
        parent.requestDisallowInterceptTouchEvent(true)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        flowTouchListener?.onTouchCapture(
            viewWidth = width,
            viewHeight = height,
            touchDownX = initialX,
            touchDownY = initialY,
            currentX = currentX,
            currentY = currentY
        )
    }

    private fun onTimeOut() {
        if (touchState != TouchState.Pending) {
            return
        }
        touchCapture()
    }

    private enum class TouchState {
        Pending,
        Capture,
        Cancel,
    }

    interface OnFlowTouchListener {
        fun onTouchCapture(
            viewWidth: Int,
            viewHeight: Int,
            touchDownX: Float,
            touchDownY: Float,
            currentX: Float,
            currentY: Float
        )

        fun onTouchMove(
            viewWidth: Int,
            viewHeight: Int,
            touchDownX: Float,
            touchDownY: Float,
            currentX: Float,
            currentY: Float
        )

        fun onTouchRelease()
    }

}