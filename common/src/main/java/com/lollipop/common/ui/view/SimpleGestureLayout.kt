package com.lollipop.common.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

class SimpleGestureLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var onTouchDownX = 0f
    private var onTouchDownY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var touchSlop = 0
    private var currentType: GestureType? = null
    private var currentState = GestureState.Pending

    private var onGestureListener: OnGestureListener? = null

    private val gestureActiveList = HashSet<GestureType>()
    var isHorizontalGestureActive = false
        private set
    var isVerticalGestureActive = false
        private set

    init {
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    }

    fun registerGesture(type: GestureType) {
        gestureActiveList.add(type)
        checkGestureActive()
    }

    fun unregisterGesture(type: GestureType) {
        gestureActiveList.remove(type)
        checkGestureActive()
    }

    private fun checkGestureActive() {
        isHorizontalGestureActive = false
        isVerticalGestureActive = false
        gestureActiveList.forEach { type ->
            if (type.isHorizontal) {
                isHorizontalGestureActive = true
            }
            if (type.isVertical) {
                isVerticalGestureActive = true
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        ev ?: return super.dispatchTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                onTouchDownX = ev.x
                onTouchDownY = ev.y
                currentState = GestureState.Pending
                currentType = null
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                currentX = ev.x
                currentY = ev.y
                onTouchMove()
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                cancelTouch()
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelTouch()
            }

            MotionEvent.ACTION_UP -> {
                onTouchUp()
            }
        }
        return super.dispatchTouchEvent(ev) || currentState != GestureState.Cancel
    }

    private fun cancelTouch() {
        parent?.requestDisallowInterceptTouchEvent(false)
        currentState = GestureState.Cancel
    }

    private fun isGestureActive(type: GestureType): Boolean {
        return gestureActiveList.contains(type)
    }

    private fun onTouchMove() {
        if (currentState == GestureState.Cancel) {
            return
        }
        when (currentState) {
            GestureState.Pending -> {
                val dx = currentX - onTouchDownX
                val dy = currentY - onTouchDownY
                val absDx = abs(dx)
                val absDy = abs(dy)
                if (absDx > touchSlop && absDx > absDy) {
                    val newGesture = if (dx > 0) {
                        GestureType.LeftToRight
                    } else {
                        GestureType.RightToLeft
                    }
                    if (isGestureActive(newGesture)) {
                        currentType = newGesture
                        currentState = GestureState.Gesture
                    } else {
                        cancelTouch()
                    }
                } else if (absDy > touchSlop && absDy > absDx) {
                    val newGesture = if (dy > 0) {
                        GestureType.TopToBottom
                    } else {
                        GestureType.BottomToTop
                    }
                    if (isGestureActive(newGesture)) {
                        currentType = newGesture
                        currentState = GestureState.Gesture
                    } else {
                        cancelTouch()
                    }
                }
            }

            GestureState.Gesture -> {
                val type = currentType ?: return
                when (type) {
                    GestureType.LeftToRight -> {
                        // 从左往右的情况下，如果X的位置小于了起点，那么就取消
                        if (currentX < onTouchDownX) {
                            cancelTouch()
                        }
                    }

                    GestureType.RightToLeft -> {
                        // 从右往左的情况下，如果X的位置大于了起点，那么就取消
                        if (currentX > onTouchDownX) {
                            cancelTouch()
                        }
                    }

                    GestureType.TopToBottom -> {
                        // 从上往下的情况下，如果Y的位置小于了起点，那么就取消
                        if (currentY < onTouchDownY) {
                            cancelTouch()
                        }
                    }

                    GestureType.BottomToTop -> {
                        // 从下往上的情况下，如果Y的位置大于了起点，那么就取消
                        if (currentY > onTouchDownY) {
                            cancelTouch()
                        }
                    }
                }
                if (currentState == GestureState.Cancel) {
                    return
                }
                if (type.isHorizontal) {
                    // 横向移动的情况下, 并且开启了纵向滑动，我们要求纵向便宜不超过两倍的touchSlop
                    if (isVerticalGestureActive && abs(currentY - onTouchDownY) > touchSlop * 2) {
                        cancelTouch()
                    }
                } else if (type.isVertical) {
                    // 纵向移动的情况下, 并且开启了横向滑动，我们要求横向便宜不超过两倍的touchSlop
                    if (isHorizontalGestureActive && abs(currentX - onTouchDownX) > touchSlop * 2) {
                        cancelTouch()
                    }
                }
            }

            GestureState.Cancel -> {}
        }
    }

    private fun onTouchUp() {
        if (currentState != GestureState.Gesture) {
            return
        }
        currentType?.let { onGestureListener?.onGesture(it) }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return currentState != GestureState.Cancel
    }

    fun onGesture(listener: OnGestureListener?) {
        onGestureListener = listener
    }

    fun interface OnGestureListener {

        fun onGesture(type: GestureType)

    }

    private enum class GestureState {
        Pending,
        Gesture,
        Cancel,
    }

    enum class GestureType {
        LeftToRight,
        RightToLeft,
        TopToBottom,
        BottomToTop;

        val isHorizontal: Boolean
            get() {
                return this == LeftToRight || this == RightToLeft
            }

        val isVertical: Boolean
            get() {
                return this == TopToBottom || this == BottomToTop
            }

    }

}