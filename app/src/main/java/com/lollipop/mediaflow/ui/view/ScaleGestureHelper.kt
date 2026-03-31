package com.lollipop.mediaflow.ui.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog

class ScaleGestureHelper(
    private val scaleGestureListener: OnScaleGestureListener
) {
    private val scaleGestureDelegate by lazy {
        ScaleGestureDelegate(::onScaleGesture)
    }

    private var viewTouchDelegate: ViewTouchDelegate? = null

    fun register(view: View) {
        this.viewTouchDelegate?.isEnable = false
        this.viewTouchDelegate = null
        val newDelegate = optTouchDelegate(view.context)
        view.setOnTouchListener(newDelegate)
    }

    fun unregister(view: View) {
        this.viewTouchDelegate?.isEnable = false
        this.viewTouchDelegate = null
        view.setOnTouchListener(null)
    }

    fun getTouchDelegate(context: Context): View.OnTouchListener {
        return optTouchDelegate(context)
    }

    private fun optTouchDelegate(context: Context): ViewTouchDelegate {
        val delegate = this.viewTouchDelegate
        if (delegate != null && delegate.isEnable) {
            return delegate
        }
        val newDelegate = ViewTouchDelegate(context, scaleGestureDelegate)
        this.viewTouchDelegate = newDelegate
        return newDelegate
    }

    private fun onScaleGesture(matrix: Matrix) {
        scaleGestureListener.onScaleGestureChanged(matrix)
    }

    fun reset() {
        scaleGestureDelegate.reset()
    }

    private class ScaleGestureDelegate(
        private val matrixCallback: (Matrix) -> Unit
    ) : ScaleGestureDetector.SimpleOnScaleGestureListener() {

        private val currentMatrix = Matrix()

        private var totalScale = 1.0f

        private var firstFactor = false

        private val log by lazy {
            registerLog()
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {

            // --- 优化点 1：引入灵敏度因子 ---
            val sensitivity = 0.8f // 调整这个值（0.5-0.8），越小越平滑，越大越跟手
            val rawFactor = detector.scaleFactor

            if (firstFactor) {
                firstFactor = false
                log.i("onScale.firstFactor = ${rawFactor}")
            }

            val smoothFactor = 1.0f + (rawFactor - 1.0f) * sensitivity

            // --- 优化点 2：计算目标缩放值 ---
            val nextScale = (totalScale * smoothFactor).coerceIn(1.0f, 5.0f)

            // 如果缩放已经到达边界（1.0 或 5.0），且还在往边界外推，直接返回
            if (nextScale == totalScale) return true

            val actualFactor = nextScale / totalScale
            totalScale = nextScale

            // --- 优化点 3：防止中心点跳变 ---
            // ScaleGestureDetector 的 focusX/Y 在手指移动时会有微小偏移
            // 导致画面“震颤”，这里直接使用当前矩阵累加
            currentMatrix.postScale(
                actualFactor,
                actualFactor,
                detector.focusX,
                detector.focusY
            )

            matrixCallback(currentMatrix)
            return true
        }

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            firstFactor = true
            return true
        }

        // 重置方法：直接调用 reset() 抹掉所有数据，回归单位矩阵
        fun reset() {
            totalScale = 1.0f
            currentMatrix.reset() // 关键：reset 会清空矩阵数据，但不销毁对象
            matrixCallback(currentMatrix)
        }
    }

    private class ViewTouchDelegate(
        context: Context,
        delegate: ScaleGestureDelegate
    ) : View.OnTouchListener {

        var isEnable = true

        private val scaleGestureDetector = ScaleGestureDetector(context, delegate)

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(
            view: View?,
            event: MotionEvent?
        ): Boolean {
            // 废弃的时候，拿来关闭
            if (!isEnable) {
                return false
            }
            event ?: return false
            return scaleGestureDetector.onTouchEvent(event)
        }

    }

    fun interface OnScaleGestureListener {
        fun onScaleGestureChanged(matrix: Matrix)
    }

}