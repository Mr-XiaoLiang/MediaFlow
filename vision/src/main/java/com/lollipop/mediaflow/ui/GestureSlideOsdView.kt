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
import kotlin.math.abs
import kotlin.math.roundToInt

class GestureSlideOsdView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatImageView(context, attrs) {

    private val scaleDrawable = ScaleDrawable()

    init {
        background = scaleDrawable
        attrs?.let {
            context.withStyledAttributes(it, R.styleable.GestureSlideOsdView) {
                setOsdColor(getColor(R.styleable.GestureSlideOsdView_android_color, Color.WHITE))
                setLineWidth(
                    getDimensionPixelSize(
                        R.styleable.GestureSlideOsdView_lineWidth,
                        10
                    ).toFloat()
                )
                setStepCount(getInt(R.styleable.GestureSlideOsdView_stepCount, 6))
                setTextSizeRatio(getFloat(R.styleable.GestureSlideOsdView_textSizeRatio, 0.2F))
                setLineTopRatio(getFloat(R.styleable.GestureSlideOsdView_lineTopRatio, 0.4F))
                setLineBottomRatio(getFloat(R.styleable.GestureSlideOsdView_lineBottomRatio, 0.75F))
                setCenterLineTopRatio(
                    getFloat(
                        R.styleable.GestureSlideOsdView_centerLineTopRatio,
                        0.25F
                    )
                )
                setCenterLineBottomRatio(
                    getFloat(
                        R.styleable.GestureSlideOsdView_centerLineBottomRatio,
                        0.75F
                    )
                )
                setTextRatio(getFloat(R.styleable.GestureSlideOsdView_textRatio, 0.18F))
            }
        }
        if (isInEditMode) {
            setTotalDuration(6 * 56 * 1000L)
            setCurrentProgress(3 * 56 * 1000L)
            setBaseWeight(0.3F)
            setCurrentPrecision(0.56F)
            setTouchX(100)
        }
    }

    /**
     * 更新总时长
     */
    fun setTotalDuration(duration: Long) {
        scaleDrawable.totalDuration = duration
    }

    /**
     * 更新当前播放进度
     */
    fun setCurrentProgress(progress: Long) {
        scaleDrawable.currentProgress = progress
    }

    /**
     * 更新基础权重，范围 0.1F ~ 1F
     */
    fun setBaseWeight(weight: Float) {
        scaleDrawable.baseWeight = weight.coerceIn(0.1F, 1F)
    }

    /**
     * 更新当前精度（百分比），范围 0.01F ~ 1F
     */
    fun setCurrentPrecision(precision: Float) {
        scaleDrawable.currentPrecision = precision.coerceIn(0.01F, 1F)
    }

    /**
     * 更新当前手势的 X 坐标
     */
    fun setTouchX(x: Int) {
        scaleDrawable.touchX = x
    }

    /**
     * 更新刻度线与文字的颜色
     */
    fun setOsdColor(color: Int) {
        scaleDrawable.color = color
    }

    /**
     * 更新刻度线宽度
     */
    fun setLineWidth(width: Float) {
        scaleDrawable.lineWidth = width
    }

    /**
     * 更新刻度线数量
     */
    fun setStepCount(count: Int) {
        scaleDrawable.stepCount = count
    }

    /**
     * 更新文字大小占 View 高度的比例
     */
    fun setTextSizeRatio(ratio: Float) {
        scaleDrawable.textSizeRatio = ratio
    }

    /**
     * 更新普通刻度线顶部占 View 高度的比例
     */
    fun setLineTopRatio(ratio: Float) {
        scaleDrawable.lineTopRatio = ratio
    }

    /**
     * 更新普通刻度线底部占 View 高度的比例
     */
    fun setLineBottomRatio(ratio: Float) {
        scaleDrawable.lineBottomRatio = ratio
    }

    /**
     * 更新中心高亮刻度线顶部占 View 高度的比例
     */
    fun setCenterLineTopRatio(ratio: Float) {
        scaleDrawable.centerLineTopRatio = ratio
    }

    /**
     * 更新中心高亮刻度线底部占 View 高度的比例
     */
    fun setCenterLineBottomRatio(ratio: Float) {
        scaleDrawable.centerLineBottomRatio = ratio
    }

    /**
     * 更新时间文字基线占 View 高度的比例
     */
    fun setTextRatio(ratio: Float) {
        scaleDrawable.textRatio = ratio
    }

    private class ScaleDrawable : Drawable() {

        /**
         * 视频的总时长
         */
        var totalDuration = 0L
            set(value) {
                field = value
                updateScale()
            }

        /**
         * 当前的播放进度
         */
        var currentProgress = 0L
            set(value) {
                field = value
                updateScale()
            }

        /**
         * 基础权重（0.1F ~ 1F)
         * 表示手势从屏幕左侧边缘滑动到屏幕右侧边缘，拖动 30% 的视频长度
         */
        var baseWeight = 0.3F
            set(value) {
                field = value
                updateScale()
            }

        /**
         * 当前百分比 0.01F ~ 1F
         * 表示叠加在 baseWeight 上的百分比，
         * 用于叠加调整手势移动速度与视频进度变化的比例
         * 比如 当值为 1F 时，表示 屏幕宽度的进度 = baseWeight * totalDuration * 1F
         * 比如 当值为 0.5F 时，表示 屏幕宽度的进度 = baseWeight * totalDuration * 0.5F
         */
        var currentPrecision = 1F
            set(value) {
                field = value
                updateScale()
            }

        /**
         * 当前手势的 X 坐标
         */
        var touchX = 0
            set(value) {
                field = value
                updateScale()
            }

        /**
         * 刻度线宽度
         */
        var lineWidth = 10F
            set(value) {
                field = value
                updateScale()
            }

        /**
         * 刻度线数量
         */
        var stepCount = 6
            set(value) {
                field = value
                updateScale()
            }

        /**
         * 刻度线与文字的颜色
         */
        var color: Int
            set(value) {
                paint.color = value
                invalidateSelf()
            }
            get() {
                return paint.color
            }

        /**
         * 上次中心文本的时间值，用于阈值判断避免重复生成字符串
         */
        private var lastCenterTextTime = -1L

        /**
         * 上次左侧文本的刻度索引，索引变化时才重新生成文本
         */
        private var lastLeftTextTickIndex = Int.MIN_VALUE

        /**
         * 上次右侧文本的刻度索引，索引变化时才重新生成文本
         */
        private var lastRightTextTickIndex = Int.MIN_VALUE

        /**
         * 上次左侧文本对应的秒级时间值，秒数变化时重新生成文本
         */
        private var lastLeftTextSecond = -1L

        /**
         * 上次右侧文本对应的秒级时间值，秒数变化时重新生成文本
         */
        private var lastRightTextSecond = -1L

        /**
         * 当前时间点（中心刻度文本）
         */
        private var currentTime = ""

        /**
         * 左侧文本（最接近 width * 0.15 的 tick 上的时间）
         */
        private var leftText = ""

        /**
         * 右侧文本（最接近 width * 0.85 的 tick 上的时间）
         */
        private var rightText = ""

        /**
         * 左侧文本 tick 的绘制 X 坐标
         */
        private var leftTextX = 0F

        /**
         * 右侧文本 tick 的绘制 X 坐标
         */
        private var rightTextX = 0F

        /**
         * 中心左侧需要绘制的刻度数量（从 centerX 到左边缘）
         */
        private var leftTickCount = 0

        /**
         * 中心右侧需要绘制的刻度数量（从 centerX 到右边缘）
         */
        private var rightTickCount = 0

        /**
         * 当前每段刻度代表的时间（ms），draw 中校验时间越界用
         */
        private var currentStepTime = 0L

        /**
         * 当前中心刻度对齐后的时间（ms），draw 中校验时间越界用
         */
        private var currentCenterSnappedTime = 0L

        /**
         * 时间文字基线的 Y 坐标（基于比例位置并扣除文字下行高度）
         */
        private var textBaselineY = 0F

        /**
         * 文字大小占 View 高度的比例
         */
        var textSizeRatio = 0.2F

        /**
         * 普通刻度线顶部占 View 高度的比例
         */
        var lineTopRatio = 0.4F

        /**
         * 普通刻度线底部占 View 高度的比例
         */
        var lineBottomRatio = 0.75F

        /**
         * 中心高亮刻度线顶部占 View 高度的比例
         */
        var centerLineTopRatio = 0.25F

        /**
         * 中心高亮刻度线底部占 View 高度的比例
         */
        var centerLineBottomRatio = 0.75F

        /**
         * 时间文字基线占 View 高度的比例
         */
        var textRatio = 0.18F

        private val paint = Paint().apply {
            isAntiAlias = true
            isDither = true
        }

        /**
         * 每段刻度的像素间距 = (width / stepCount) * stretch
         */
        private var scaleStepWidth = 0F

        /**
         * 中心刻度的 X 坐标（对齐 touchX）
         */
        private var centerX = 0F

        /**
         * 根据播放进度转换为时间点点方法
         */
        private fun getTimeValue(ms: Long): String {
            return DisplayFormater.formatTime(ms)
        }

        override fun onBoundsChange(bounds: Rect) {
            super.onBoundsChange(bounds)
            updateScale()
        }

        private fun updateScale() {
            if (bounds.isEmpty) {
                scaleStepWidth = 0F
                return
            }
            val width = bounds.width().toFloat()
            val height = bounds.height().toFloat()

            // 有效的当前进度，限制在总时长范围内
            val progress = currentProgress.coerceIn(0L, totalDuration)

            // 基础间距
            val baseStepWidth = width / stepCount

            // === 直接用 precision 缩放刻度间距 ===
            // precision 越小（精细操作）→ 间距越大（坐标系拉伸）
            // precision 越大（粗略操作）→ 间距越小（坐标系压缩）
            scaleStepWidth = (baseStepWidth / currentPrecision.coerceIn(0.01F, 1.0F))
                .coerceIn(baseStepWidth, baseStepWidth * 50F)

            // 中心刻度位置：对齐手势 touchX
            centerX = touchX.toFloat().coerceIn(0F, width)

            // === 计算每个刻度代表的时间 ===
            // 屏幕全宽代表的时间跨度 = totalDuration * baseWeight * precision
            val spanTime = (totalDuration * baseWeight * currentPrecision).toLong()
            val stepTime = if (spanTime > 0) spanTime / stepCount else 0L

            // 中心刻度对齐到 stepTime 边界
            val centerSnappedTime = if (stepTime > 0) {
                (progress / stepTime) * stepTime
            } else {
                progress
            }
            currentStepTime = stepTime
            currentCenterSnappedTime = centerSnappedTime

            // 中心文本：直接用真实的 progress（不做 stepTime 对齐），秒数变化时重新生成
            val currentSecond = progress / 1000L
            if (currentSecond != lastCenterTextTime || lastCenterTextTime < 0) {
                currentTime = getTimeValue(progress)
                lastCenterTextTime = currentSecond
            }

            // === 计算左右刻度数量（从中心填满到屏幕边缘） ===
            // 进度到开头/结尾时，对应的方向不画刻度，表示"到头了"
            leftTickCount = if (scaleStepWidth > 0F && progress > 0L) {
                (centerX / scaleStepWidth).toInt() + 1
            } else 0
            rightTickCount = if (scaleStepWidth > 0F && progress < totalDuration) {
                ((width - centerX) / scaleStepWidth).toInt() + 1
            } else 0

            // === 在 centerX 左右各 30% 屏幕宽度的位置放置文本 ===
            val leftTextTargetX = centerX - width * 0.3F
            val rightTextTargetX = centerX + width * 0.3F

            val newLeftTextTickIndex = if (scaleStepWidth > 0F) {
                ((leftTextTargetX - centerX) / scaleStepWidth).roundToInt()
            } else 0
            val newRightTextTickIndex = if (scaleStepWidth > 0F) {
                ((rightTextTargetX - centerX) / scaleStepWidth).roundToInt()
            } else 0

            leftTextX = centerX + newLeftTextTickIndex * scaleStepWidth
            rightTextX = centerX + newRightTextTickIndex * scaleStepWidth

            // 重新生成文本的条件：刻度索引变了 OR 秒级时间变了
            val leftTime = (centerSnappedTime + newLeftTextTickIndex * stepTime)
                .coerceIn(0L, totalDuration)
            val leftSecond = leftTime / 1000L
            val leftNeedRegenerate = newLeftTextTickIndex != lastLeftTextTickIndex
                    || abs(leftSecond - lastLeftTextSecond) > 0L
            if (leftNeedRegenerate && newLeftTextTickIndex != 0) {
                leftText = getTimeValue(leftTime)
                lastLeftTextSecond = leftSecond
            }
            val rightTime = (centerSnappedTime + newRightTextTickIndex * stepTime)
                .coerceIn(0L, totalDuration)
            val rightSecond = rightTime / 1000L
            val rightNeedRegenerate = newRightTextTickIndex != lastRightTextTickIndex
                    || abs(rightSecond - lastRightTextSecond) > 0L
            if (rightNeedRegenerate && newRightTextTickIndex != 0) {
                rightText = getTimeValue(rightTime)
                lastRightTextSecond = rightSecond
            }
            lastLeftTextTickIndex = newLeftTextTickIndex
            lastRightTextTickIndex = newRightTextTickIndex

            // === 统一拦截：不展示左右文本的条件（按优先级排列） ===

            // 1. 文本刻度与中心是同一个刻度 → 清空
            if (newLeftTextTickIndex == 0) {
                leftText = ""
            }
            if (newRightTextTickIndex == 0) {
                rightText = ""
            }
            if (newLeftTextTickIndex == newRightTextTickIndex && newLeftTextTickIndex != 0) {
                rightText = ""
            }

            // 2. 刻度对应的时间超出视频有效范围 (< 0 或 > totalDuration)
            if (stepTime > 0L) {
                val leftTickTime = centerSnappedTime + newLeftTextTickIndex * stepTime
                val rightTickTime = centerSnappedTime + newRightTextTickIndex * stepTime
                if (leftTickTime < 0L || leftTickTime > totalDuration) {
                    leftText = ""
                }
                if (rightTickTime < 0L || rightTickTime > totalDuration) {
                    rightText = ""
                }
            }

            if (leftText.isEmpty()) {
                lastLeftTextTickIndex = 0
                lastLeftTextSecond = -1L
            }
            if (rightText.isEmpty()) {
                lastRightTextTickIndex = 0
                lastRightTextSecond = -1L
            }

            // === 预计算绘制所需参数 ===
            paint.textSize = height * textSizeRatio
            textBaselineY = height * textRatio - paint.descent()

            invalidateSelf()
        }

        override fun draw(canvas: Canvas) {
            if (bounds.isEmpty || scaleStepWidth <= 0F) {
                return
            }
            val height = bounds.height().toFloat()
            val width = bounds.width().toFloat()

            // 预计算的绘制参数（两类刻度高度）
            val baseTop = height * lineTopRatio
            val baseBottom = height * lineBottomRatio
            val textTop = height * centerLineTopRatio
            val textBottom = height * centerLineBottomRatio

            paint.strokeWidth = lineWidth
            paint.style = Paint.Style.STROKE

            // 左侧刻度（不含中心 i=0），跳过时间越界的刻度
            for (i in -leftTickCount..-1) {
                // 刻度对应的时间超出视频范围 → 跳过
                val tickTime = currentCenterSnappedTime + i * currentStepTime
                if (currentStepTime > 0L && (tickTime < 0L || tickTime > totalDuration)) {
                    continue
                }
                val x = centerX + i * scaleStepWidth
                if (x < 0F || x > width) {
                    continue
                }
                val isTextTick = i == lastLeftTextTickIndex || i == lastRightTextTickIndex
                canvas.drawLine(x, if (isTextTick) textTop else baseTop,
                    x, if (isTextTick) textBottom else baseBottom, paint)
            }

            // 右侧刻度（不含中心 i=0），跳过时间越界的刻度
            for (i in 1..rightTickCount) {
                // 刻度对应的时间超出视频范围 → 跳过
                val tickTime = currentCenterSnappedTime + i * currentStepTime
                if (currentStepTime > 0L && (tickTime < 0L || tickTime > totalDuration)) {
                    continue
                }
                val x = centerX + i * scaleStepWidth
                if (x < 0F || x > width) {
                    continue
                }
                val isTextTick = i == lastLeftTextTickIndex || i == lastRightTextTickIndex
                canvas.drawLine(x, if (isTextTick) textTop else baseTop,
                    x, if (isTextTick) textBottom else baseBottom, paint)
            }

            // 中心高亮刻度线（始终使用 textTop/textBottom）
            canvas.drawLine(centerX, textTop, centerX, textBottom, paint)

            // === 绘制时间文字（所有坐标和文本均在 updateScale 中预计算） ===
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = height * textSizeRatio

            if (currentTime.isNotEmpty()) {
                canvas.drawText(currentTime, centerX, textBaselineY, paint)
            }
            if (leftText.isNotEmpty() && lastLeftTextTickIndex != 0) {
                canvas.drawText(leftText, leftTextX, textBaselineY, paint)
            }
            if (rightText.isNotEmpty() && lastRightTextTickIndex != 0) {
                canvas.drawText(rightText, rightTextX, textBaselineY, paint)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int {
            return android.graphics.PixelFormat.TRANSLUCENT
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
            invalidateSelf()
        }

    }

}