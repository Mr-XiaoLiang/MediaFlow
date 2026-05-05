package com.lollipop.common.ui.view

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewParent
import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import androidx.core.content.withStyledAttributes
import com.lollipop.common.R
import kotlin.math.absoluteValue

class DeconstructSlider(
    context: Context,
    attr: AttributeSet? = null
) : View(context, attr) {

    private val deconstructProgress = DeconstructProgress()

    var sliderChangeListener: SliderChangeListener? = null

    private var gestureHost: FlowPlayerGestureHost? = null

    private var sliderLeft = 0
    private var sliderRight = 0
    private var decorationWidth = 0
    private var initialX = 0F
    private var initialY = 0F
    private var currentX = 0F
    private var currentY = 0F
    private var touchState = TouchState.Pending
    var progress = 0F
        private set
    private var touchSlop = 0

    init {
        background = deconstructProgress
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        attr?.let {
            context.withStyledAttributes(it, R.styleable.DeconstructSlider) {
                val activeColor = getColor(
                    R.styleable.DeconstructSlider_activeColor,
                    Color.GRAY
                )
                val inactiveColor = getColor(
                    R.styleable.DeconstructSlider_inactiveColor,
                    Color.GRAY
                )
                val activeHeight = getDimensionPixelSize(
                    R.styleable.DeconstructSlider_activeHeight,
                    10
                )
                val inactiveHeight = getDimensionPixelSize(
                    R.styleable.DeconstructSlider_inactiveHeight,
                    10
                )
                val gapWidth = getDimensionPixelSize(
                    R.styleable.DeconstructSlider_gapWidth,
                    10
                )
                setColor(activeColor, inactiveColor)
                setSize(activeHeight, inactiveHeight, gapWidth)
            }
        }

        if (isInEditMode) {
            setProgress(0.6F)
        }
    }

    fun setProgress(p: Float) {
        setProgress(p, false)
    }

    private fun setProgress(p: Float, fromUser: Boolean) {
        this.progress = p
        if (progress < 0F) {
            progress = 0F
        } else if (progress > 1F) {
            progress = 1F
        }
        if (deconstructProgress.setLevel(DeconstructProgress.getLevel(progress))) {
            invalidate()
        }
        sliderChangeListener?.onProgressChanged(progress, fromUser)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        deconstructProgress.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
        updateTouchBounds()
    }

    private fun updateTouchBounds() {
        val contentLength = width - paddingLeft - paddingRight - decorationWidth
        sliderLeft = paddingLeft + (deconstructProgress.activeHeight / 2)
        sliderRight = sliderLeft + contentLength
    }

    private fun cancelTouch() {
        sliderChangeListener?.onTouchUp()
        touchState = TouchState.Cancel
        parent.requestDisallowInterceptTouchEvent(false)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) {
            return super.onTouchEvent(event)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = event.x
                initialY = event.y
                currentX = initialX
                currentY = initialY
                touchState = TouchState.Pending
                parent.requestDisallowInterceptTouchEvent(true)
                sliderChangeListener?.onTouchDown()
            }

            MotionEvent.ACTION_MOVE -> {
                onTouchMove(event.x, event.y)
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
        return touchState != TouchState.Cancel
    }

    private fun onTouchMove(x: Float, y: Float) {
        val offsetX = x - currentX
        currentX = x
        currentY = y
        when (touchState) {
            TouchState.Pending -> {
                val dx = currentX - initialX
                val dy = currentY - initialY
                if (dx.absoluteValue > touchSlop) {
                    touchCapture()
                } else if (dy.absoluteValue > touchSlop) {
                    cancelTouch()
                }
            }

            TouchState.Capture -> {
                parent.requestDisallowInterceptTouchEvent(true)
                val offsetProgress = offsetX * 1F / (sliderRight - sliderLeft)
                setProgress(offsetProgress + progress, true)
            }

            TouchState.Cancel -> {
                // 不做任何事
            }
        }
    }

    private fun touchCapture() {
        touchState = TouchState.Capture
        parent.requestDisallowInterceptTouchEvent(true)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun setColor(active: Int, inactive: Int) {
        this.deconstructProgress.setColor(active, inactive)
    }

    fun setSize(active: Int, inactive: Int, gap: Int) {
        this.decorationWidth = (active + inactive) / 2
        this.deconstructProgress.setSize(active, inactive, gap)
        updateTouchBounds()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        registerGestureHost()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        gestureHost?.unregisterPenetrate(this)
        gestureHost = null
    }

    private fun registerGestureHost() {
        if (isInEditMode) {
            return
        }
        var viewParent: ViewParent? = parent
        while (viewParent != null) {
            if (viewParent is FlowPlayerGestureHost) {
                gestureHost = viewParent
                viewParent.registerPenetrate(this)
                return
            }
            if (viewParent is View) {
                viewParent = viewParent.parent
            }
        }
    }

    private enum class TouchState {
        Pending,
        Capture,
        Cancel,
    }

    private class DeconstructProgress : Drawable() {

        companion object {
            private const val PROGRESS_MAX = 10000L
            private const val PROGRESS_MIN = 0L

            fun getLevel(progress: Float): Int {
                return ((PROGRESS_MAX - PROGRESS_MIN) * progress + PROGRESS_MIN).toInt()
            }
        }

        private val contentPadding = Rect()

        var activeHeight: Int = 10
            private set
        var inactiveHeight: Int = 5
            private set
        var gapWidth: Int = 10
            private set

        private val activeRect = RectF()
        private var activeRadius = 0F
        private val inactiveRect = RectF()
        private var inactiveRadius = 0F

        private val activePaint = Paint().apply {
            isAntiAlias = true
            isDither = true
            style = Paint.Style.FILL
        }

        private val inactivePaint = Paint().apply {
            isAntiAlias = true
            isDither = true
            style = Paint.Style.FILL
        }

        @IntRange(from = PROGRESS_MIN, to = PROGRESS_MAX)
        private var progressLevel = 0

        private fun progress(): Float {
            return (progressLevel * 1F / (PROGRESS_MAX - PROGRESS_MIN)) + PROGRESS_MIN
        }

        fun setColor(active: Int, inactive: Int) {
            this.activePaint.color = active
            this.inactivePaint.color = inactive
            invalidateSelf()
        }

        fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
            this.contentPadding.set(left, top, right, bottom)
            buildContent()
            invalidateSelf()
        }

        fun setSize(active: Int, inactive: Int, gap: Int) {
            this.activeHeight = active
            this.inactiveHeight = inactive
            this.gapWidth = gap
            buildContent()
            invalidateSelf()
        }

        override fun onLevelChange(level: Int): Boolean {
            if (level == progressLevel) {
                return false
            }
            progressLevel = if (level < PROGRESS_MIN) {
                PROGRESS_MIN.toInt()
            } else if (level > PROGRESS_MAX) {
                PROGRESS_MAX.toInt()
            } else {
                level
            }
            buildContent()
            return true

        }

        override fun onBoundsChange(bounds: Rect) {
            super.onBoundsChange(bounds)
            buildContent()
            invalidateSelf()
        }

        private fun buildContent() {
            val rect = bounds
            if (rect.isEmpty) {
                activeRect.set(0F, 0F, 0F, 0F)
                inactiveRect.set(0F, 0F, 0F, 0F)
                return
            }
            val padding = contentPadding
            val contentWidth = (rect.width() - padding.left - padding.right).toFloat()
            val contentHeight = (rect.height() - padding.top - padding.bottom).toFloat()
            val left = (rect.left + contentPadding.left).toFloat()
            val top = (rect.top + contentPadding.top).toFloat()
            val right = left + contentWidth
            val centerY = (contentHeight / 2) + top

            val progress = progress()
            val progressFullWidth = contentWidth - activeHeight - inactiveHeight - gapWidth
            val activeWidth = progressFullWidth * progress
            val activeHalf = activeHeight * 0.5F
            val inactiveHalf = inactiveHeight * 0.5F
            activeRect.set(
                left,
                centerY - activeHalf,
                left + activeWidth + activeHeight,
                centerY + activeHalf
            )
            activeRadius = activeHalf
            inactiveRect.set(
                activeRect.right + gapWidth,
                centerY - inactiveHalf,
                right,
                centerY + inactiveHalf
            )
            inactiveRadius = inactiveHalf
        }

        override fun draw(canvas: Canvas) {
            if (!activeRect.isEmpty) {
                canvas.drawRoundRect(activeRect, activeRadius, activeRadius, activePaint)
            }
            if (!inactiveRect.isEmpty) {
                canvas.drawRoundRect(inactiveRect, inactiveRadius, inactiveRadius, inactivePaint)
            }
        }

        override fun getOpacity(): Int {
            return PixelFormat.TRANSPARENT
        }

        override fun setAlpha(alpha: Int) {
            activePaint.alpha = alpha
            inactivePaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            activePaint.colorFilter = colorFilter
            inactivePaint.colorFilter = colorFilter
        }

    }

    interface SliderChangeListener {

        fun onTouchDown()

        fun onTouchUp()

        fun onProgressChanged(@FloatRange(from = 0.0, to = 1.0) progress: Float, fromUser: Boolean)

    }

    class AnimationDelegate(
        private val slider: DeconstructSlider
    ) {

        private companion object {
            private const val PROGRESS_MIN = 0F
            private const val PROGRESS_MAX = 1F
            private const val DURATION = 200L
        }

        private var defaultActiveHeight = 0
        private var defaultInactiveHeight = 0
        private var defaultGapWidth = 0

        private var touchedActiveHeight = 0
        private var touchedInactiveHeight = 0
        private var touchedGapWidth = 0

        private val defaultActiveColor = ColorHelper()
        private val defaultInactiveColor = ColorHelper()

        private val touchedActiveColor = ColorHelper()
        private val touchedInactiveColor = ColorHelper()

        private var animationProgress = 0F

        private val animator by lazy {
            ValueAnimator().also { anim ->
                anim.addUpdateListener {
                    val animatedValue = it.animatedValue
                    if (animatedValue is Float) {
                        onUpdate(animatedValue)
                    }
                }
            }
        }

        fun defaultSize(active: Int, inactive: Int, gap: Int) {
            this.defaultActiveHeight = active
            this.defaultInactiveHeight = inactive
            this.defaultGapWidth = gap
        }

        fun touchedSize(active: Int, inactive: Int, gap: Int) {
            this.touchedActiveHeight = active
            this.touchedInactiveHeight = inactive
            this.touchedGapWidth = gap
        }

        fun defaultColor(active: Int, inactive: Int) {
            this.defaultActiveColor.set(active)
            this.defaultInactiveColor.set(inactive)
        }

        fun touchedColor(active: Int, inactive: Int) {
            this.touchedActiveColor.set(active)
            this.touchedInactiveColor.set(inactive)
        }

        private fun onUpdate(p: Float) {
            val activeColor = getProgressValue(p, defaultActiveColor, touchedActiveColor)
            val inactiveColor = getProgressValue(p, defaultInactiveColor, touchedInactiveColor)
            slider.setColor(activeColor, inactiveColor)
            val activeHeight = getProgressValue(p, defaultActiveHeight, touchedActiveHeight)
            val inactiveHeight = getProgressValue(p, defaultInactiveHeight, touchedInactiveHeight)
            val gapWidth = getProgressValue(p, defaultGapWidth, touchedGapWidth)
            slider.setSize(activeHeight, inactiveHeight, gapWidth)
        }

        private fun getProgressValue(progress: Float, min: ColorHelper, max: ColorHelper): Int {
            if (max.value == min.value) {
                return max.value
            }
            return Color.argb(
                (((max.a - min.a) * progress) + min.a).toInt().coerceAtMost(255).coerceAtLeast(0),
                (((max.r - min.r) * progress) + min.r).toInt().coerceAtMost(255).coerceAtLeast(0),
                (((max.g - min.g) * progress) + min.g).toInt().coerceAtMost(255).coerceAtLeast(0),
                (((max.b - min.b) * progress) + min.b).toInt().coerceAtMost(255).coerceAtLeast(0)
            )
        }

        private fun getProgressValue(progress: Float, min: Int, max: Int): Int {
            if (max == min) {
                return max
            }
            return (((max - min) * progress) + min).toInt()
        }

        fun onTouchDown() {
            startAnimation(PROGRESS_MAX)
        }

        fun onTouchUp() {
            startAnimation(PROGRESS_MIN)
        }

        fun resetToDefault() {
            slider.setColor(
                active = defaultActiveColor.value,
                inactive = defaultInactiveColor.value
            )
            slider.setSize(
                active = defaultActiveHeight,
                inactive = defaultInactiveHeight,
                gap = defaultGapWidth
            )
        }

        private fun startAnimation(end: Float) {
            animator.cancel()
            val length = (animationProgress - end).absoluteValue
            val duration = (length / (PROGRESS_MAX - PROGRESS_MIN) * DURATION).toLong()
            animator.setFloatValues(animationProgress, end)
            animator.duration = duration
            animator.start()
        }

        private class ColorHelper {
            var value = 0
                private set
            var a = 0
                private set
            var r = 0
                private set
            var g = 0
                private set
            var b = 0
                private set

            fun set(color: Int) {
                this.value = color
                this.a = Color.alpha(color)
                this.r = Color.red(color)
                this.g = Color.green(color)
                this.b = Color.blue(color)
            }
        }

    }

}