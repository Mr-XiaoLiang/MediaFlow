package com.lollipop.mediaflow.ui.view

import android.content.Context
import android.graphics.Matrix
import android.graphics.Rect
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import com.lollipop.mediaflow.tools.task
import kotlin.math.absoluteValue
import kotlin.math.sqrt

/**
 * 此处我们固定只处理纵向滚动的ViewPager2下，横向的手势分发
 */
class FlowPlayerGestureHost @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val state = State()

    private val longPressTask = task {
        onTimeOut()
    }

    var flowTouchListener: OnFlowTouchListener? = null

    private val penetrateViewList = mutableListOf<View>()
    private val tempViewBounds = Rect()

    init {
        state.init(context)
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

    private fun onScaleGestureChanged(matrix: Matrix) {
        flowTouchListener?.onDoubleScale(matrix)
    }

    fun resetScaleGesture() {
        state.resetMatrix()
        onScaleGestureChanged(state.scaleMatrix)
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
                state.touchDown(e.x, e.y)
                // 如果属于穿透区域，那么就放弃事件
                if (isPenetrate(state.initialX, state.initialY)) {
                    longPressTask.cancel()
                    return super.dispatchTouchEvent(e)
                }
                parent.requestDisallowInterceptTouchEvent(true)
                longPressTask.cancel()
                longPressTask.delayOnUI(state.longPressTimeout)
            }

            MotionEvent.ACTION_MOVE -> {
                if (state.touchMode == TouchMode.Double) {
                    onDoubleTouchMove(e)
                } else {
                    onSingleTouchMove(e.x, e.y)
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (state.touchMode == TouchMode.Single && e.pointerCount > 1) {
                    // 如果在单手指的模式下，按下多个手指，那也是放弃，只处理一个指头的情况
                    cancelTouch()
                } else if (state.touchMode == TouchMode.Pending && e.pointerCount == 2) {
                    // 如果已经进入了其他模式，那么就不会再进入多指模式了，
                    // 但是如果还没有进入其他模式，那么可以直接进入多指模式
                    state.touchMode = TouchMode.Double
                    onDoubleTouchBegin(e)
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // 如果双指手势的情况下，又收回了手指，那么就放弃了
                if (state.touchMode == TouchMode.Double && e.pointerCount < 2) {
                    cancelTouch()
                }
            }

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                // 结束了
                cancelTouch()
            }

            else -> {

            }
        }
        return super.dispatchTouchEvent(e) || state.isCapture
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        // 一旦进入 Capture 状态，立刻拦截，不再让子 View 收到任何事件
        if (state.isCapture) {
            return true
        }
        return super.onInterceptTouchEvent(e)
    }

    private fun onDoubleTouchBegin(event: MotionEvent) {
        if (event.pointerCount != 2) {
            return
        }
        parent.requestDisallowInterceptTouchEvent(true)
        state.doubleTouchBegin.fill(event)
        state.doubleTouchCurrent.clone(state.doubleTouchBegin)
    }

    private fun onDoubleTouchMove(event: MotionEvent) {
        if (event.pointerCount != 2) {
            cancelTouch()
            return
        }
        parent.requestDisallowInterceptTouchEvent(true)
        val doubleTouch = state.doubleTouchCurrent
        val lastDistance = doubleTouch.distance
        val lastFocusX = doubleTouch.focusX
        val lastFocusY = doubleTouch.focusY
        doubleTouch.fill(event)
        val currentDistance = doubleTouch.distance
        val currentFocusX = doubleTouch.focusX
        val currentFocusY = doubleTouch.focusY

        // 1. 计算缩放倍率 (当前距离 / 上次距离)
        val factor = currentDistance / lastDistance

        // 2. 更新全局缩放倍数并限制范围
        val nextScale = (state.totalScale * factor).coerceIn(1.0f, 5.0f)
        val actualFactor = nextScale / state.totalScale
        state.totalScale = nextScale

        // 3. 应用矩阵：先缩放，再平移（跟随手指中心点位移）
        state.scaleMatrix.postScale(actualFactor, actualFactor, currentFocusX, currentFocusY)

        // 【额外修正】让画面中心跟随手指位移（可选，增加跟手感）
        state.scaleMatrix.postTranslate(currentFocusX - lastFocusX, currentFocusY - lastFocusY)

        onScaleGestureChanged(state.scaleMatrix)
    }

    private fun onSingleTouchMove(x: Float, y: Float) {
        state.currentX = x
        state.currentY = y
        when (state.touchMode) {
            TouchMode.Pending -> {
                val dx = state.dx
                val dy = state.dy
                if (dx.absoluteValue > state.touchSlop * 2) {
                    touchSingleCapture()
                } else if (dy.absoluteValue > state.touchSlop) {
                    cancelTouch()
                }
            }

            TouchMode.Single -> {
                parent.requestDisallowInterceptTouchEvent(true)
                flowTouchListener?.onSingleMove(
                    viewWidth = width,
                    viewHeight = height,
                    touchDownX = state.initialX,
                    touchDownY = state.initialY,
                    currentX = state.currentX,
                    currentY = state.currentY
                )
            }

            TouchMode.Double -> {

            }

            TouchMode.Cancel -> {
                // 不做任何事
            }
        }
    }

    private fun cancelTouch() {
        if (state.isCapture) {
            flowTouchListener?.onTouchRelease()
        }
        state.touchMode = TouchMode.Cancel
        longPressTask.cancel()
        parent.requestDisallowInterceptTouchEvent(false)
    }

    private fun touchSingleCapture() {
        state.touchMode = TouchMode.Single
        parent.requestDisallowInterceptTouchEvent(true)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        flowTouchListener?.onSingleCapture(
            viewWidth = width,
            viewHeight = height,
            touchDownX = state.initialX,
            touchDownY = state.initialY,
            currentX = state.currentX,
            currentY = state.currentY
        )
    }

    private fun onTimeOut() {
        if (state.touchMode != TouchMode.Pending) {
            return
        }
        touchSingleCapture()
    }

    private class State {
        var touchSlop = 0
        var longPressTimeout = 0L
        var initialX = 0f
        var initialY = 0f
        var currentX = 0F
        var currentY = 0F
        var touchMode = TouchMode.Pending

        var totalScale = 1F

        val doubleTouchBegin = DoubleTouch()
        val doubleTouchCurrent = DoubleTouch()

        val scaleMatrix = Matrix()

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
                return touchMode == TouchMode.Single || touchMode == TouchMode.Double
            }

        fun init(context: Context) {
            touchSlop = ViewConfiguration.get(context).scaledTouchSlop
            // 获取系统默认长按时间并适当减小，以提升视频操作的爽快感，结果大约在 350ms 左右
            longPressTimeout = (ViewConfiguration.getLongPressTimeout() * 0.7).toLong()
        }

        fun touchDown(x: Float, y: Float) {
            initialX = x
            initialY = y
            currentX = x
            currentY = y
            touchMode = TouchMode.Pending
        }

        fun resetMatrix() {
            scaleMatrix.reset()
            totalScale = 1F
        }

    }

    private class DoubleTouch {
        var xA: Float = 0F
            private set
        var yA: Float = 0F
            private set
        var xB: Float = 0F
            private set
        var yB: Float = 0F
            private set

        var distance: Float = 0F
            private set

        var focusX: Float = 0F
            private set

        var focusY: Float = 0F
            private set

        fun fill(event: MotionEvent) {
            xA = event.getX(0)
            xB = event.getX(1)
            yA = event.getY(0)
            yB = event.getY(1)
            distance = distance()
            focusX = (xA + xB) / 2f
            focusY = (yA + yB) / 2f
        }

        fun clone(touch: DoubleTouch) {
            this.xA = touch.xA
            this.xB = touch.xB
            this.yA = touch.yA
            this.yB = touch.yB
            this.distance = touch.distance
            this.focusX = touch.focusX
            this.focusY = touch.focusY
        }

        private fun distance(): Float {
            val dx = (xA - xB).toDouble()
            val dy = (yA - yB).toDouble()
            return sqrt(dx * dx + dy * dy).toFloat()
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
        Single,

        /**
         * 捕获中
         * 双手指
         */
        Double,

        /**
         * 被取消
         */
        Cancel;

    }

    interface OnFlowTouchListener {
        fun onSingleCapture(
            viewWidth: Int,
            viewHeight: Int,
            touchDownX: Float,
            touchDownY: Float,
            currentX: Float,
            currentY: Float
        )

        fun onSingleMove(
            viewWidth: Int,
            viewHeight: Int,
            touchDownX: Float,
            touchDownY: Float,
            currentX: Float,
            currentY: Float
        )

        fun onDoubleScale(matrix: Matrix)

        fun onTouchRelease()

    }

}