package com.lollipop.mediaflow.ui

import android.content.Context
import android.graphics.Outline
import android.graphics.Path
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.core.content.withStyledAttributes
import com.lollipop.mediaflow.R
import kotlin.math.min

class RoundOutlineLayout @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null
) : FrameLayout(context, attributeSet) {

    init {
        var gravity = 0
        var radius = 0F
        attributeSet?.let {
            context.withStyledAttributes(it, R.styleable.RoundOutlineLayout) {
                gravity = getInt(R.styleable.RoundOutlineLayout_android_gravity, 0)
                radius = getDimension(R.styleable.RoundOutlineLayout_android_radius, 0F)
            }
        }

        clipToOutline = true
        outlineProvider = RoundOutlineDelegate(
            gravity = gravity,
            radius = radius,
            getLayoutDirection = ::getLayoutDirection
        )
    }

    private class RoundOutlineDelegate(
        val gravity: Int,
        val radius: Float,
        val getLayoutDirection: () -> Int
    ) : ViewOutlineProvider() {

        private val outlinePath = Path()

        private fun maxRadius(width: Int, height: Int): Float {
            return min(width, height) * 0.5F
        }

        private fun setMaxRound(view: View, outline: Outline) {
            val width = view.width
            val height = view.height
            val maxRadius = maxRadius(width, height)
            outline.setRoundRect(0, 0, width, height, maxRadius)
        }

        private fun setRound(view: View, outline: Outline) {
            val width = view.width
            val height = view.height
            outline.setRoundRect(0, 0, width, height, radius)
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
                // 横向的
                when (horizontalGravity) {
                    Gravity.CENTER_HORIZONTAL -> {
                        setRound(view, outline)
                    }

                    Gravity.RIGHT -> {
                        outlinePath.reset()
                        val width = view.width
                        val height = view.height
                        val maxRadius = maxRadius(width, height)
                        // radii 顺序 [top left],[top right],[bottom right],[bottom left]
                        outlinePath.addRoundRect(
                            0F, 0F,
                            width.toFloat(), height.toFloat(),
                            floatArrayOf(
                                radius, radius,
                                maxRadius, maxRadius,
                                maxRadius, maxRadius,
                                radius, radius
                            ), Path.Direction.CW
                        )
                        outline.setPath(outlinePath)
                    }

                    Gravity.LEFT -> {
                        outlinePath.reset()
                        val width = view.width
                        val height = view.height
                        val maxRadius = maxRadius(width, height)
                        // radii 顺序 [top left],[top right],[bottom right],[bottom left]
                        outlinePath.addRoundRect(
                            0F, 0F,
                            width.toFloat(), height.toFloat(),
                            floatArrayOf(
                                maxRadius, maxRadius,
                                radius, radius,
                                radius, radius,
                                maxRadius, maxRadius
                            ), Path.Direction.CW
                        )
                        outline.setPath(outlinePath)
                    }

                    else -> {
                        setMaxRound(view, outline)
                    }
                }
            } else if (verticalGravity != 0) {
                // 纵向的
                when (verticalGravity) {
                    Gravity.TOP -> {
                        outlinePath.reset()
                        val width = view.width
                        val height = view.height
                        val maxRadius = maxRadius(width, height)
                        // radii 顺序 [top left],[top right],[bottom right],[bottom left]
                        outlinePath.addRoundRect(
                            0F, 0F,
                            width.toFloat(), height.toFloat(),
                            floatArrayOf(
                                maxRadius, maxRadius,
                                maxRadius, maxRadius,
                                radius, radius,
                                radius, radius
                            ), Path.Direction.CW
                        )
                        outline.setPath(outlinePath)
                    }

                    Gravity.CENTER_VERTICAL -> {
                        setRound(view, outline)
                    }

                    Gravity.BOTTOM -> {
                        outlinePath.reset()
                        val width = view.width
                        val height = view.height
                        val maxRadius = maxRadius(width, height)
                        // radii 顺序 [top left],[top right],[bottom right],[bottom left]
                        outlinePath.addRoundRect(
                            0F, 0F,
                            width.toFloat(), height.toFloat(),
                            floatArrayOf(
                                radius, radius,
                                radius, radius,
                                maxRadius, maxRadius,
                                maxRadius, maxRadius
                            ), Path.Direction.CW
                        )
                        outline.setPath(outlinePath)
                    }

                    else -> {
                        setMaxRound(view, outline)
                    }
                }
            } else {
                setMaxRound(view, outline)
            }
        }
    }

}