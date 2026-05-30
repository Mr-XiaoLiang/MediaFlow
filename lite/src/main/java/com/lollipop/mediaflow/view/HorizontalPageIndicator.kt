package com.lollipop.mediaflow.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.lollipop.mediaflow.R
import kotlin.math.max
import kotlin.math.min
import androidx.core.content.withStyledAttributes

class HorizontalPageIndicator @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null
) : AppCompatImageView(context, attributeSet) {

    private val indicatorDrawable = PageIndicatorDrawable()

    /**
     * 激活的指示器颜色
     */
    var activeColor: Int
        set(value) {
            indicatorDrawable.activeColor = value
        }
        get() {
            return indicatorDrawable.activeColor
        }

    /**
     * 未激活的指示器颜色
     */
    var inactiveColor: Int
        set(value) {
            indicatorDrawable.inactiveColor = value
        }
        get() {
            return indicatorDrawable.inactiveColor
        }

    /**
     * 指示器数量
     */
    var indicatorCount: Int
        set(value) {
            indicatorDrawable.indicatorCount = value
        }
        get() {
            return indicatorDrawable.indicatorCount
        }

    /**
     * 当前激活的指示器
     */
    var indicatorIndex: Int
        set(value) {
            indicatorDrawable.indicatorIndex = value
        }
        get() {
            return indicatorDrawable.indicatorIndex
        }

    /**
     * 指示器在屏幕上滑动时候的偏移量
     * 用来制作滑动过程
     */
    var indicatorOffset: Float
        set(value) {
            indicatorDrawable.indicatorOffset = value
        }
        get() {
            return indicatorDrawable.indicatorOffset
        }

    /**
     * 指示器的半径
     */
    var indicatorRadius: Float
        set(value) {
            indicatorDrawable.indicatorRadius = value
        }
        get() {
            return indicatorDrawable.indicatorRadius
        }

    /**
     * 指示器之间的间隔
     */
    var indicatorInterval: Float
        set(value) {
            indicatorDrawable.indicatorInterval = value
        }
        get() {
            return indicatorDrawable.indicatorInterval
        }

    private var viewPagerDelegate: PageIndicatorDelegate? = null

    init {
        setImageDrawable(indicatorDrawable)
        attributeSet?.let { attr ->
            context.withStyledAttributes(attr, R.styleable.HorizontalPageIndicator) {
                indicatorInterval = getDimensionPixelSize(
                    R.styleable.HorizontalPageIndicator_indicatorInterval,
                    0
                ).toFloat()
                activeColor = getColor(
                    R.styleable.HorizontalPageIndicator_activeColor,
                    Color.WHITE
                )
                inactiveColor = getColor(
                    R.styleable.HorizontalPageIndicator_inactiveColor,
                    Color.GRAY
                )
                indicatorRadius = getDimensionPixelSize(
                    R.styleable.HorizontalPageIndicator_indicatorRadius,
                    10
                ).toFloat()
            }
        }
        if (isInEditMode) {
            indicatorCount = 5
            indicatorIndex = 1
            indicatorOffset = 0.5F
        }
    }

    fun bind(viewPager: ViewPager2) {
        viewPagerDelegate?.unbind()
        val newDelegate = PageIndicatorDelegate(viewPager, this)
        newDelegate.bind()
        viewPagerDelegate = newDelegate
    }

    private class PageIndicatorDelegate(
        private val viewPager: ViewPager2,
        private val pageIndicator: HorizontalPageIndicator
    ) {

        private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                pageIndicator.indicatorIndex = position
                pageIndicator.indicatorOffset = 0F
            }

            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int
            ) {
                pageIndicator.indicatorIndex = position
                pageIndicator.indicatorOffset = positionOffset
            }
        }

        private val adapterDataObserver = object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                onCountChanged()
            }

            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                onCountChanged()
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                onCountChanged()
            }

            private fun onCountChanged() {
                pageIndicator.indicatorCount = viewPager.adapter?.itemCount ?: 0
            }
        }

        fun bind() {
            viewPager.adapter?.registerAdapterDataObserver(adapterDataObserver)
            viewPager.registerOnPageChangeCallback(pageChangeCallback)
            pageIndicator.indicatorCount = viewPager.adapter?.itemCount ?: 0
        }

        fun unbind() {
            viewPager.adapter?.unregisterAdapterDataObserver(adapterDataObserver)
            viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
        }

    }

    private class PageIndicatorDrawable : Drawable() {

        private val inactivePaint = Paint().apply {
            isDither = true
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        private val activePaint = Paint().apply {
            isDither = true
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        /**
         * 激活的指示器颜色
         */
        var activeColor: Int
            set(value) {
                activePaint.color = value
                invalidateSelf()
            }
            get() {
                return activePaint.color
            }

        /**
         * 未激活的指示器颜色
         */
        var inactiveColor: Int
            set(value) {
                inactivePaint.color = value
                invalidateSelf()
            }
            get() {
                return inactivePaint.color
            }

        /**
         * 指示器数量
         */
        var indicatorCount: Int = 0
            set(value) {
                field = value
                invalidateSelf()
            }

        /**
         * 当前激活的指示器
         */
        var indicatorIndex: Int = 0
            set(value) {
                field = value
                invalidateSelf()
            }

        /**
         * 指示器在屏幕上滑动时候的偏移量
         * 用来制作滑动过程
         */
        var indicatorOffset: Float = 0F
            set(value) {
                field = min(1F, max(0F, value))
                invalidateSelf()
            }

        /**
         * 指示器的半径
         */
        var indicatorRadius: Float = 10F
            set(value) {
                field = value
                invalidateSelf()
            }

        /**
         * 指示器之间的间隔
         */
        var indicatorInterval: Float = 10F
            set(value) {
                field = value
                invalidateSelf()
            }

        override fun draw(canvas: Canvas) {
            drawIndicator(canvas)
        }

        private fun drawIndicator(canvas: Canvas) {
            if (bounds.isEmpty) {
                return
            }
            val canvasRadius = min(bounds.width(), bounds.height()) * 0.5F
            val canvasCenterY = bounds.exactCenterY()
            val canvasCenterX = bounds.exactCenterX()
            val pointY = canvasRadius - indicatorRadius
            val intervalAngle = DeviceHelper.arcDimenToAngle(
                canvasRadius,
                indicatorInterval + indicatorRadius * 2
            )
            // 而获取到扫过的角度之后，除以2，是为了整体的居中
            val startAngle = (getIndicatorSweepAngle(intervalAngle) / 2)
            for (i in 0 until indicatorCount) {
                // 默认顺时针记录角度，但是我们的常规指示器是从左到右的，所以我们是做减法
                val angle = startAngle - (i * intervalAngle)
                val saveCount = canvas.save()
                // 移动到中间
                canvas.translate(canvasCenterX, canvasCenterY)
                // 绕着中心旋转
                canvas.rotate(angle)
                // 画一个圆形
                canvas.drawCircle(0F, pointY, indicatorRadius, inactivePaint)
                // 换回来
                canvas.restoreToCount(saveCount)
            }
            val activeAngle = startAngle - ((indicatorIndex + indicatorOffset) * intervalAngle)
            val saveCount = canvas.save()
            // 移动到中间
            canvas.translate(canvasCenterX, canvasCenterY)
            // 绕着中心旋转
            canvas.rotate(activeAngle)
            // 画一个圆形
            canvas.drawCircle(0F, pointY, indicatorRadius, activePaint)
            // 换回来
            canvas.restoreToCount(saveCount)
        }

        private fun getIndicatorSweepAngle(intervalAngle: Float): Float {
            return intervalAngle * (indicatorCount - 1)
        }

        override fun setAlpha(alpha: Int) {
            activePaint.alpha = alpha
            inactivePaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            activePaint.colorFilter = colorFilter
            inactivePaint.colorFilter = colorFilter
        }

        override fun getOpacity(): Int {
            return PixelFormat.TRANSPARENT
        }
    }

}