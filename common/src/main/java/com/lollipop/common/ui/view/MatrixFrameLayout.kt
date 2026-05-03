package com.lollipop.common.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.util.AttributeSet
import android.widget.FrameLayout

class MatrixFrameLayout @JvmOverloads constructor(
    context: Context, attributeSet: AttributeSet? = null
) : FrameLayout(context, attributeSet) {

    private var canvasMatrix: Matrix? = null

    fun updateMatrix(matrix: Matrix?) {
        this.canvasMatrix = matrix
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        val currentMatrix = canvasMatrix
        if (currentMatrix == null) {
            super.dispatchDraw(canvas)
            return
        }
        val saveCount = canvas.save()
        canvas.concat(currentMatrix)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(saveCount)
    }

}