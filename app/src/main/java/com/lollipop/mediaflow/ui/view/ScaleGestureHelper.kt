package com.lollipop.mediaflow.ui.view

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
        val newDelegate = ViewTouchDelegate(view.context, scaleGestureDelegate)
        this.viewTouchDelegate = newDelegate
        view.setOnTouchListener(newDelegate)
    }

    fun unregister(view: View) {
        this.viewTouchDelegate?.isEnable = false
        this.viewTouchDelegate = null
        view.setOnTouchListener(null)
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

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val nextScale = (totalScale * scaleFactor).coerceIn(1.0f, 5.0f)

            // 计算本次真正的缩放比例（因为 totalScale 受到了 coerceIn 的限制）
            val actualFactor = nextScale / totalScale
            totalScale = nextScale

            // 【关键】直接在当前矩阵上累加缩放
            // 它会自动保留之前的位移和缩放状态
            currentMatrix.postScale(actualFactor, actualFactor, detector.focusX, detector.focusY)

            matrixCallback(currentMatrix)
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
        context: Context, delegate: ScaleGestureDelegate
    ) : View.OnTouchListener {

        var isEnable = true

        private val scaleGestureDetector = ScaleGestureDetector(context, delegate)

        private val log by lazy {
            registerLog()
        }

        override fun onTouch(
            view: View?,
            event: MotionEvent?
        ): Boolean {
            log.i("onTouch")
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