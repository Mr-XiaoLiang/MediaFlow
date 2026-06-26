package com.lollipop.mediaflow.tools

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.view.isInvisible
import kotlin.math.abs

class MainMenuAnimationHelper(
    private val menuBar: View
) {

    companion object {
        private const val DURATION = 300L
        private const val PROGRESS_CLOSE = 0.0f
        private const val PROGRESS_OPEN = 1.0f
        private fun isClose(progress: Float): Boolean {
            return abs(progress - PROGRESS_CLOSE) < 0.001f
        }
    }

    private var currentProgress = PROGRESS_CLOSE

    val isRunning: Boolean
        get() {
            return valueAnimator.isRunning
        }

    var direction: Direction = Direction.CLOSE
        private set

    private val valueAnimator = ValueAnimator().apply {
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener { animation ->
            val value = animation.animatedValue
            if (value is Float) {
                update(value)
            }
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                menuBar.isInvisible = isClose(currentProgress)
            }

            override fun onAnimationStart(animation: Animator) {
                super.onAnimationStart(animation)
                menuBar.isInvisible = false
            }
        })
    }

    fun preInit() {
        menuBar.isInvisible = true
        update(PROGRESS_CLOSE)
        direction = Direction.CLOSE
    }

    private fun startAnimation(target: Float) {
        valueAnimator.cancel()
        val offset = abs(currentProgress - target)
        valueAnimator.setFloatValues(currentProgress, target)
        // PROGRESS_OPEN - PROGRESS_CLOSE = 1.0f
        // 所以忽略 Progress 的长度，直接用偏移量乘以 DURATION 即可
        valueAnimator.duration = (offset * DURATION).toLong()
        valueAnimator.start()
    }

    fun open() {
        direction = Direction.OPEN
        startAnimation(PROGRESS_OPEN)
    }

    fun close() {
        direction = Direction.CLOSE
        startAnimation(PROGRESS_CLOSE)
    }

    fun toggle() {
        when (direction) {
            Direction.OPEN -> {
                close()
            }

            Direction.CLOSE -> {
                open()
            }
        }
    }

    private fun update(progress: Float) {
        currentProgress = progress
        menuBar.translationX = menuBar.height * (1.0f - progress)
        menuBar.alpha = progress
        menuBar.isInvisible = isClose(progress)
    }

    enum class Direction {
        OPEN,
        CLOSE
    }

}