package com.lollipop.mediaflow.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.withStyledAttributes
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.tools.DisplayFormater

class GestureSlideOsdView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatImageView(context, attrs) {

    private val uiParams = UiParams()

    private val scaleDrawable = ScaleDrawable(uiParams)

    init {
        background = scaleDrawable
        attrs?.let {
            context.withStyledAttributes(it, R.styleable.GestureSlideOsdView) {
                updateUiParams {
                    color = getColor(R.styleable.GestureSlideOsdView_android_color, Color.WHITE)
                    lineWidth = getDimensionPixelSize(
                        R.styleable.GestureSlideOsdView_lineWidth,
                        10
                    ).toFloat()
                    stepCount = getInt(R.styleable.GestureSlideOsdView_stepCount, 6)

                    textSizeRatio = getFloat(R.styleable.GestureSlideOsdView_textSizeRatio, 0.2F)
                    defaultLineTopRatio =
                        getFloat(R.styleable.GestureSlideOsdView_defaultLineTopRatio, 0.4F)
                    defaultLineBottomRatio =
                        getFloat(R.styleable.GestureSlideOsdView_defaultLineBottomRatio, 0.75F)
                    highLineTopRatio =
                        getFloat(R.styleable.GestureSlideOsdView_highLineTopRatio, 0.25F)
                    highLineBottomRatio =
                        getFloat(R.styleable.GestureSlideOsdView_highLineBottomRatio, 0.75F)
                    textRatio = getFloat(R.styleable.GestureSlideOsdView_textRatio, 0.18F)
                }
            }
        }
        if (isInEditMode) {
            setTotalDuration(6 * 56 * 1000L)
            setCurrentProgress(3 * 56 * 1000L)
            setBaseWeight(0.3F)
            setCurrentPrecision(0F)
            setCurrentPrecision(1F)
            setTouchX(100)
        }
    }

    private inline fun updateUiParams(block: UiParams.() -> Unit) {
        block(uiParams)
        uiParams.notifyChanged()
    }

    /**
     * 更新总时长
     */
    fun setTotalDuration(duration: Long) {
        updateUiParams {
            totalDuration = duration
        }
    }

    /**
     * 更新当前播放进度
     */
    fun setCurrentProgress(progress: Long) {
        updateUiParams {
            currentProgress = progress
        }
    }

    /**
     * 更新基础权重，范围 0.1F ~ 1F
     */
    fun setBaseWeight(weight: Float) {
        updateUiParams {
            baseWeight = weight.coerceIn(0.1F, 1F)
        }
    }

    /**
     * 更新当前精度（百分比），范围 0.01F ~ 1F
     */
    fun setCurrentPrecision(precision: Float) {
        updateUiParams {
            currentPrecision = precision.coerceIn(0.01F, 1F)
        }
    }

    /**
     * 更新当前手势的 X 坐标
     */
    fun setTouchX(x: Int) {
        updateUiParams {
            touchX = x
        }
    }

    /**
     * 更新刻度线与文字的颜色
     */
    fun setOsdColor(c: Int) {
        updateUiParams {
            color = c
        }
    }

    /**
     * 更新刻度线宽度
     */
    fun setLineWidth(width: Float) {
        updateUiParams {
            lineWidth = width
        }
    }

    /**
     * 更新刻度线数量
     */
    fun setStepCount(count: Int) {
        updateUiParams {
            stepCount = count
        }
    }

    /**
     * 更新文字大小占 View 高度的比例
     */
    fun setTextSizeRatio(ratio: Float) {
        updateUiParams {
            textSizeRatio = ratio
        }
    }

    /**
     * 更新普通刻度线顶部占 View 高度的比例
     */
    fun setDefaultLineTopRatio(ratio: Float) {
        updateUiParams {
            defaultLineTopRatio = ratio
        }
    }

    /**
     * 更新普通刻度线底部占 View 高度的比例
     */
    fun setDefaultLineBottomRatio(ratio: Float) {
        updateUiParams {
            defaultLineBottomRatio = ratio
        }
    }

    /**
     * 更新高亮刻度线顶部占 View 高度的比例
     */
    fun setHighLineTopRatio(ratio: Float) {
        updateUiParams {
            highLineTopRatio = ratio
        }
    }

    /**
     * 更新高亮刻度线底部占 View 高度的比例
     */
    fun setHighLineBottomRatio(ratio: Float) {
        updateUiParams {
            highLineBottomRatio = ratio
        }
    }

    /**
     * 更新时间文字基线占 View 高度的比例
     */
    fun setTextRatio(ratio: Float) {
        updateUiParams {
            textRatio = ratio
        }
    }

    private class ScaleDrawable(private val uiParams: UiParams) : Drawable() {

        private val state = State()

        private var textBaselineY = 0F
        private var defaultLineTop = 0F
        private var defaultLineBottom = 0F
        private var highLineTop = 0F
        private var highLineBottom = 0F

        private val linePaint = Paint().apply {
            isAntiAlias = true
            isDither = true
            style = Paint.Style.STROKE
        }

        private val textPaint = Paint().apply {
            isAntiAlias = true
            isDither = true
            textAlign = Paint.Align.CENTER
        }

        init {
            uiParams.onChangedCallback = ::updateScale
        }

        override fun onBoundsChange(bounds: Rect) {
            super.onBoundsChange(bounds)
            updateScale()
        }

        private fun updateScale() {
            if (bounds.isEmpty) {
                return
            }
            val width = bounds.width()
            val height = bounds.height()
            state.update(
                totalDuration = uiParams.totalDuration,
                stepCount = uiParams.stepCount,
                baseWeight = uiParams.baseWeight,
                viewWidth = width,
            )
            textPaint.textSize = height * uiParams.textSizeRatio
            linePaint.strokeWidth = uiParams.lineWidth

            textBaselineY = height * uiParams.textRatio

            defaultLineTop = height * uiParams.defaultLineTopRatio
            defaultLineBottom = height * uiParams.defaultLineBottomRatio
            highLineTop = height * uiParams.highLineTopRatio
            highLineBottom = height * uiParams.highLineBottomRatio

            linePaint.color = uiParams.color
            textPaint.color = uiParams.color

            invalidateSelf()
        }

        override fun draw(canvas: Canvas) {
            if (bounds.isEmpty) {
                return
            }

            state.forEach(
                progress = uiParams.currentProgress,
                precision = uiParams.currentPrecision,
            ) { line ->
                val lineX = line.lineX.toFloat()
                val timeValue = line.timeValue

                linePaint.alpha = line.alpha

                val lineTop: Float
                val lineBottom: Float
                if (timeValue.isNotEmpty()) {
                    lineTop = highLineTop
                    lineBottom = highLineBottom
                } else {
                    lineTop = defaultLineTop
                    lineBottom = defaultLineBottom
                }

                canvas.drawLine(
                    lineX, lineTop,
                    lineX, lineBottom,
                    linePaint
                )

                if (timeValue.isNotEmpty()) {
                    textPaint.alpha = line.alpha
                    canvas.drawText(timeValue, lineX, textBaselineY, textPaint)
                }

            }
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int {
            return android.graphics.PixelFormat.TRANSLUCENT
        }

        override fun setAlpha(alpha: Int) {
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            linePaint.colorFilter = colorFilter
            textPaint.colorFilter = colorFilter
            invalidateSelf()
        }

    }

    private class UiParams {

        var onChangedCallback: () -> Unit = {}

        /**
         * 视频的总时长
         */
        var totalDuration = 0L

        /**
         * 当前的播放进度
         */
        var currentProgress = 0L

        /**
         * 基础权重（0.1F ~ 1F)
         */
        var baseWeight = 0.3F

        /**
         * 当前百分比 0.01F ~ 1F
         * 表示叠加在 baseWeight 上的百分比，
         * 用于叠加调整手势移动速度与视频进度变化的比例
         */
        var currentPrecision = 1F

        /**
         * 当前手势的 X 坐标
         */
        var touchX = 0

        /**
         * 刻度线宽度
         */
        var lineWidth = 10F

        /**
         * 刻度线数量
         */
        var stepCount = 6

        /**
         * 刻度线与文字的颜色
         */
        var color: Int = Color.WHITE

        /**
         * 文字大小占 View 高度的比例
         */
        var textSizeRatio = 0.2F

        /**
         * 普通刻度线顶部占 View 高度的比例
         */
        var defaultLineTopRatio = 0.4F

        /**
         * 普通刻度线底部占 View 高度的比例
         */
        var defaultLineBottomRatio = 0.75F

        /**
         * 高亮刻度线顶部占 View 高度的比例
         */
        var highLineTopRatio = 0.3F

        /**
         * 高亮刻度线底部占 View 高度的比例
         */
        var highLineBottomRatio = 0.8F

        /**
         * 时间文字基线占 View 高度的比例
         */
        var textRatio = 0.18F

        fun notifyChanged() {
            onChangedCallback()
        }

    }

    private class State {

        val timeFlagList = mutableListOf<String>()
        var stepWidth = 0
            private set
        var stepTime = 0L
            private set

        var timeValueInterval = 0
            private set

        private val lastSnapshot = StateSnapshot()
        private val currentSnapshot = StateSnapshot()

        private val tempLineInfo = LineInfo()

        fun forEach(progress: Long, precision: Float, callback: (LineInfo) -> Unit) {
            val weight = 1F / precision
            val viewWidth = currentSnapshot.viewWidth
            val centerX = viewWidth * 0.5F
            val currentStepWidth = (stepWidth * weight).toLong()
            val currentStepTime = (stepTime * weight).toLong()
            val weightProgress = (progress * weight).toLong()
            val leftIndex = weightProgress / currentStepTime
            // 应该向左移动的距离
            val offsetX =
                ((weightProgress % currentStepTime) * 1F / currentStepTime * currentStepWidth * -1).toInt()

            val halfFlagCount = ((centerX / currentStepWidth) + 1).toInt()

            val startIndex = leftIndex - halfFlagCount

            val startX = (centerX + offsetX - (halfFlagCount * currentStepWidth)).toInt()

            val lineCount = halfFlagCount * 2 + 1
            val maxIndex = timeFlagList.size - 1

            for (i in 0 until lineCount) {
                val index = (startIndex + i).toInt()
                if (index < 0) {
                    continue
                }
                if (index > maxIndex) {
                    break
                }
                val lineX = (startX + (i * currentStepWidth)).toInt()
                tempLineInfo.lineX = lineX
                tempLineInfo.alpha = alphaInt(lineX, 0, viewWidth)
                if (index % timeValueInterval == 0) {
                    tempLineInfo.timeValue = timeFlagList[index]
                } else {
                    tempLineInfo.timeValue = ""
                }
                callback(tempLineInfo)
            }
        }

        private fun alphaInt(value: Int, min: Int, max: Int): Int {
            val intValue = (alpha(value, min, max) * 255).toInt()
            if (intValue < 0) {
                return 0
            }
            if (intValue > 255) {
                return 255
            }
            return intValue
        }

        private fun alpha(value: Int, min: Int, max: Int): Float {
            // 范围外直接返回 0
            if (value !in min..max) {
                return 0f
            }

            // 区间长度为 0，只有 value 恰好命中才为 1
            if (min == max) {
                return 1f
            }

            val distToMin = value - min
            val distToMax = max - value
            val closest = if (distToMin < distToMax) {
                distToMin
            } else {
                distToMax
            }

            // range = max - min，因为已经保证 max > min，所以 > 0
            val range = max - min
            return 2f * closest / range
        }

        fun update(
            totalDuration: Long,
            stepCount: Int,
            baseWeight: Float,
            viewWidth: Int,
        ): Boolean {
            currentSnapshot.totalDuration = totalDuration
            currentSnapshot.stepCount = stepCount
            currentSnapshot.baseWeight = baseWeight
            currentSnapshot.viewWidth = viewWidth
            val changed = lastSnapshot.isChanged(currentSnapshot)
            if (!changed) {
                return false
            }

            lastSnapshot.update(currentSnapshot)
            timeFlagList.clear()
            val stepWeight = baseWeight / stepCount
            stepTime = (totalDuration * stepWeight).toLong()
            stepWidth = (viewWidth * 1F / stepCount).toInt()
            // 时间戳间隔
            timeValueInterval = (viewWidth * 0.25F / stepWidth).toInt()
            var tempTime = 0L
            var index = 0
            while (tempTime < totalDuration) {
                // 跳过注定不会被显示的部分，以此来进一步减少消耗
                if (index % timeValueInterval == 0) {
                    timeFlagList.add(getTimeValue(tempTime))
                } else {
                    timeFlagList.add("")
                }
                tempTime += stepTime
                index++
            }
            if (tempTime != totalDuration) {
                timeFlagList.add(getTimeValue(totalDuration))
            }
            return true
        }

        /**
         * 根据播放进度转换为时间点点方法
         */
        private fun getTimeValue(ms: Long): String {
            return DisplayFormater.formatTime(ms)
        }

    }

    private class LineInfo {
        var lineX = 0
        var timeValue = ""
        var alpha = 255
    }

    private class StateSnapshot {
        var totalDuration = 0L
        var stepCount = 6
        var baseWeight = 0.3F
        var viewWidth = 0

        fun isChanged(other: StateSnapshot): Boolean {
            if (this.totalDuration != other.totalDuration) {
                return true
            }
            if (this.stepCount != other.stepCount) {
                return true
            }
            if (this.baseWeight - other.baseWeight > 0.0001F) {
                return true
            }
            if (this.viewWidth != other.viewWidth) {
                return true
            }
            return false
        }

        fun update(other: StateSnapshot) {
            this.totalDuration = other.totalDuration
            this.stepCount = other.stepCount
            this.baseWeight = other.baseWeight
            this.viewWidth = other.viewWidth
        }

    }

}