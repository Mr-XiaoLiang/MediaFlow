package com.lollipop.common.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.core.content.withStyledAttributes
import com.lollipop.common.R
import kotlin.math.min

class RoundOutlineLayout @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null
) : FrameLayout(context, attributeSet) {

    private var outlineDelegate: RoundOutlineDelegate? = null

    var isLightStrokeEnable = true
        set(value) {
            field = value
            invalidate()
        }

    var lightStrokeWidth: Float
        set(value) {
            lightPaint.strokeWidth = value
            outlineDelegate?.let {
                it.strokeWidth = value
            }
            invalidateOutline()
        }
        get() {
            return lightPaint.strokeWidth
        }

    private val lightPaint by lazy {
        Paint().apply {
            isAntiAlias = true
            isDither = true
            color = Color.WHITE
            style = Paint.Style.STROKE
        }
    }

    init {
        var gravity = 0
        var radius = 0F
        attributeSet?.let {
            context.withStyledAttributes(it, R.styleable.RoundOutlineLayout) {
                gravity = getInt(R.styleable.RoundOutlineLayout_android_gravity, 0)
                radius = getDimension(R.styleable.RoundOutlineLayout_android_radius, 0F)
                isLightStrokeEnable = getBoolean(R.styleable.RoundOutlineLayout_strokeEnable, true)
                lightStrokeWidth = getDimensionPixelSize(
                    R.styleable.RoundOutlineLayout_strokeWidth, 3
                ).toFloat()
            }
        }

        clipToOutline = true
        val delegate = RoundOutlineDelegate(
            gravity = gravity,
            radius = radius,
            getLayoutDirection = ::getLayoutDirection
        )
        outlineProvider = delegate
        outlineDelegate = delegate
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateLightStrokeShader(width, height)
    }

    fun updateLightStrokeShader(viewWidth: Int, viewHeight: Int) {
        val shader = LinearGradient(
            0F, 0F, 0F, viewHeight.toFloat(),
            intArrayOf(
                whiteByAlpha(0.4F),
                Color.TRANSPARENT,
                whiteByAlpha(0.25F)
            ),
            floatArrayOf(0F, 0.5F, 1F),
            Shader.TileMode.CLAMP
        )
        lightPaint.setShader(shader)
        invalidate()
    }

    private fun whiteByAlpha(alpha: Float): Int {
        return Color.argb(
            (255 * alpha).toInt().coerceIn(0, 255),
            255,
            255,
            255
        )
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (isLightStrokeEnable) {
            outlineDelegate?.let { delegate ->
                val strokePath = delegate.strokePath
                if (!strokePath.isEmpty) {
                    canvas.drawPath(strokePath, lightPaint)
                }
            }
        }
    }

    private class RoundOutlineDelegate(
        val gravity: Int,
        val radius: Float,
        val getLayoutDirection: () -> Int
    ) : ViewOutlineProvider() {

        private val outlinePath = Path()
        val strokePath = Path()
        var strokeWidth = 0F

        private fun maxRadius(width: Int, height: Int): Float {
            return min(width, height) * 0.5F
        }

        private fun buildStrokePath(width: Int, height: Int, radii: FloatArray) {
            strokePath.reset()
            val halfStroke = strokeWidth * 0.5F
            strokePath.addRoundRect(
                halfStroke,
                halfStroke,
                width - halfStroke,
                height - halfStroke,
                radii,
                Path.Direction.CW
            )
        }

        private fun buildStrokePath(width: Int, height: Int, r: Float) {
            strokePath.reset()
            val halfStroke = strokeWidth * 0.5F
            strokePath.addRoundRect(
                halfStroke,
                halfStroke,
                width - halfStroke,
                height - halfStroke,
                r, r,
                Path.Direction.CW
            )
        }

        private fun setMaxRound(view: View, outline: Outline) {
            val width = view.width
            val height = view.height
            val maxRadius = maxRadius(width, height)
            buildStrokePath(width, height, maxRadius)
            outline.setRoundRect(0, 0, width, height, maxRadius)
        }

        private fun setRound(view: View, outline: Outline) {
            val width = view.width
            val height = view.height
            buildStrokePath(width, height, radius)
            outline.setRoundRect(0, 0, width, height, radius)
        }

        private fun setRound(view: View, outline: Outline, radii: FloatArray) {
            val width = view.width
            val height = view.height
            buildStrokePath(width, height, radii)
            outlinePath.reset()
            outlinePath.addRoundRect(
                0F, 0F,
                width.toFloat(), height.toFloat(),
                radii,
                Path.Direction.CW
            )
            outline.setPath(outlinePath)
        }

        override fun getOutline(view: View?, outline: Outline?) {
            view ?: return
            outline ?: return
            if (gravity == 0) {
                setMaxRound(view, outline)
                return
            }
            val absoluteGravity = Gravity.getAbsoluteGravity(gravity, getLayoutDirection())
            val verticalGravity = gravity and Gravity.VERTICAL_GRAVITY_MASK
            val horizontalGravity = absoluteGravity and Gravity.HORIZONTAL_GRAVITY_MASK
            if (horizontalGravity != 0) {
                when (horizontalGravity) {
                    Gravity.RIGHT -> {
                        val maxRadius = maxRadius(view.width, view.height)
                        // radii 顺序 [top left],[top right],[bottom right],[bottom left]
                        setRound(
                            view,
                            outline,
                            floatArrayOf(
                                radius, radius,
                                maxRadius, maxRadius,
                                maxRadius, maxRadius,
                                radius, radius
                            )
                        )
                    }

                    Gravity.LEFT -> {
                        val maxRadius = maxRadius(view.width, view.height)
                        // radii 顺序 [top left],[top right],[bottom right],[bottom left]
                        setRound(
                            view,
                            outline,
                            floatArrayOf(
                                maxRadius, maxRadius,
                                radius, radius,
                                radius, radius,
                                maxRadius, maxRadius
                            )
                        )
                    }

                    else -> {
                        setRound(view, outline)
                    }
                }
            } else if (verticalGravity != 0) {
                when (verticalGravity) {
                    Gravity.TOP -> {
                        val maxRadius = maxRadius(view.width, view.height)
                        // radii 顺序 [top left],[top right],[bottom right],[bottom left]
                        setRound(
                            view,
                            outline,
                            floatArrayOf(
                                maxRadius, maxRadius,
                                maxRadius, maxRadius,
                                radius, radius,
                                radius, radius
                            )
                        )
                    }

                    Gravity.BOTTOM -> {
                        val maxRadius = maxRadius(view.width, view.height)
                        // radii 顺序 [top left],[top right],[bottom right],[bottom left]
                        setRound(
                            view,
                            outline,
                            floatArrayOf(
                                radius, radius,
                                radius, radius,
                                maxRadius, maxRadius,
                                maxRadius, maxRadius
                            )
                        )
                    }

                    else -> {
                        setRound(view, outline)
                    }
                }
            } else {
                setRound(view, outline)
            }
        }
    }

}