package com.lollipop.mediaflow.ui

import android.util.TypedValue
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import kotlin.math.max

abstract class BasicInsetsActivity : AppCompatActivity() {

    protected val log by lazy {
        registerLog()
    }

    private var leftGuideline: View? = null
    private var topGuideline: View? = null
    private var rightGuideline: View? = null
    private var bottomGuideline: View? = null
    private var minEdge: Int = 0

    protected var insetsCache = Insets.NONE
        private set

    protected fun initInsetsListener(rootView: View) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val finallyInsets = Insets.of(
                max(systemBars.left, displayCutout.left),
                max(systemBars.top, displayCutout.top),
                max(systemBars.right, displayCutout.right),
                max(systemBars.bottom, displayCutout.bottom)
            )
            insetsCache = finallyInsets
            onWindowInsetsChanged(
                insetsCache.left,
                insetsCache.top,
                insetsCache.right,
                insetsCache.bottom
            )
            updateGuidelineInsets(
                insetsCache.left,
                insetsCache.top,
                insetsCache.right,
                insetsCache.bottom
            )
            insets
        }
    }

    protected abstract fun onWindowInsetsChanged(
        left: Int, top: Int, right: Int, bottom: Int
    )

    protected open fun onGuidelineInsetsChanged(
        left: Int, top: Int, right: Int, bottom: Int
    ) {
    }

    protected fun bindGuidelineInsets(
        leftGuideline: View,
        topGuideline: View,
        rightGuideline: View,
        bottomGuideline: View,
        minEdgeDp: Float = 16F
    ) {
        minEdge = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            minEdgeDp,
            resources.displayMetrics
        ).toInt()
        this.leftGuideline = leftGuideline
        this.topGuideline = topGuideline
        this.rightGuideline = rightGuideline
        this.bottomGuideline = bottomGuideline
    }

    private fun updateGuidelineInsets(
        left: Int, top: Int, right: Int, bottom: Int
    ) {
        log.i("updateGuidelineInsets: $left, $top, $right, $bottom")
        val guidelineInsets = Insets.of(
            maxOf(left, minEdge),
            maxOf(top, minEdge),
            maxOf(right, minEdge),
            maxOf(bottom, minEdge)
        )
        onGuidelineInsetsChanged(
            guidelineInsets.left,
            guidelineInsets.top,
            guidelineInsets.right,
            guidelineInsets.bottom
        )
        try {
            leftGuideline?.updateLayoutParams<ConstraintLayout.LayoutParams> {
                guideBegin = guidelineInsets.left
            }
        } catch (e: Throwable) {
            log.e("updateGuidelineInsets: left", e)
        }
        try {
            topGuideline?.updateLayoutParams<ConstraintLayout.LayoutParams> {
                guideBegin = guidelineInsets.top
            }
        } catch (e: Throwable) {
            log.e("updateGuidelineInsets: top", e)
        }
        try {
            rightGuideline?.updateLayoutParams<ConstraintLayout.LayoutParams> {
                guideEnd = guidelineInsets.right
            }
        } catch (e: Throwable) {
            log.e("updateGuidelineInsets: right", e)
        }
        try {
            bottomGuideline?.updateLayoutParams<ConstraintLayout.LayoutParams> {
                guideEnd = guidelineInsets.bottom
            }
        } catch (e: Throwable) {
            log.e("updateGuidelineInsets: bottom", e)
        }
    }

}