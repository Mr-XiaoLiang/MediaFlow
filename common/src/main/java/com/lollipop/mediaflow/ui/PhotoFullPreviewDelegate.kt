package com.lollipop.mediaflow.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.lollipop.common.tools.LLog.Companion.registerLog
import kotlin.math.abs
import kotlin.math.min

class PhotoFullPreviewDelegate(
    private val activity: AppCompatActivity,
    private val onPreviewHideCallback: () -> Unit
) {

    companion object {
        const val DURATION = 300L
        const val PROGRESS_FROM = 0F
        const val PROGRESS_TO = 1F

        const val PROGRESS_CLOSE = PROGRESS_FROM + 0.01F

        const val PROGRESS_OPEN = PROGRESS_TO - 0.01F

        const val BACKGROUND_COLOR = 0xCC000000.toInt()
    }

    private val log = registerLog()

    private val backPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            log.i("handleOnBackPressed")
            hidePreview()
        }
    }

    private val photoView = SubsamplingScaleImageView(activity).also {
        it.setOnClickListener {
            log.i("SubsamplingScaleImageView.OnClick")
            if (backPressedCallback.isEnabled) {
                hidePreview()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private val touchMaskView = View(activity).also {
        it.setOnTouchListener { _, _ -> true }
    }

    private val previewPanel = FrameLayout(activity).also {
        it.addView(
            touchMaskView,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        it.addView(
            photoView,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        it.isInvisible = true
    }

    private var currentItemView: View? = null

    private val anchor = Anchor()

    private var animationProgress = PROGRESS_FROM

    private val animatorListener = object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            log.i("AnimatorListenerAdapter.onAnimationEnd")
            onAnimationEnd()
        }
    }

    private val updateListener = ValueAnimator.AnimatorUpdateListener { anim ->
        val animatedValue = anim.animatedValue
        if (animatedValue is Float) {
            onAnimationUpdate(animatedValue)
        }
    }

    private val valueAnimation = ValueAnimator().also {
        it.addListener(animatorListener)
        it.addUpdateListener(updateListener)
    }


    fun onCreate() {
        log.i("onCreate")
        activity.findViewById<View>(android.R.id.content)?.let { contentView ->
            if (contentView is ViewGroup) {
                contentView.addView(
                    previewPanel,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        }
        activity.onBackPressedDispatcher.addCallback(backPressedCallback)
    }

    fun show(uri: Uri, itemView: View?) {
        log.i("show: $uri, $itemView")
        backPressedCallback.isEnabled = true
        this.currentItemView = itemView
        loadNewPhoto(uri)
        showStartAnimation()
    }

    private fun loadNewPhoto(uri: Uri) {
        log.i("loadNewPhoto: $uri")
        photoView.resetScaleAndCenter()
        photoView.setImage(ImageSource.uri(uri))
    }

    private fun showStartAnimation() {
        log.i("showStartAnimation")
        valueAnimation.cancel()
        val itemView = this.currentItemView

        // 设置为显示
        previewPanel.isVisible = true

        if (itemView == null) {
            log.i("showStartAnimation : itemView == null")
            // 没法做动画的情况，就不做动画了
            animationProgress = PROGRESS_TO
            onAnimationEnd()
            return
        }

        itemView.translationZ = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            10F,
            activity.resources.displayMetrics
        )

        // 准备做动画的话，背景色先清空
        previewPanel.setBackgroundColor(Color.TRANSPARENT)
        // 然后隐藏照片
        photoView.alpha = 0F

        updateAnchor(itemView)

        valueAnimation.duration = DURATION
        valueAnimation.setFloatValues(PROGRESS_FROM, PROGRESS_TO)
        valueAnimation.start()
    }

    private fun showHideAnimation() {
        log.i("showHideAnimation")
        valueAnimation.cancel()
        val current = animationProgress
        valueAnimation.setFloatValues(current, PROGRESS_FROM)
        val maxProgress = abs(PROGRESS_TO - PROGRESS_FROM)
        valueAnimation.duration = (abs(current - PROGRESS_FROM) / maxProgress * DURATION).toLong()
        valueAnimation.start()
    }

    private fun hidePreview() {
        log.i("hidePreview")
        previewPanel.setBackgroundColor(Color.TRANSPARENT)
        currentItemView?.isVisible = true
        val animateScaleAndCenter = photoView.animateScaleAndCenter(
            photoView.minScale,
            photoView.center
        )
        if (animateScaleAndCenter != null) {
            animateScaleAndCenter.withOnAnimationEventListener(
                ScaleImageViewAnimationListener {
                    doHidePreview()
                }
            )

            val currentScaleOffset = abs(photoView.scale - photoView.minScale)
            val scaleWeight = currentScaleOffset / (photoView.maxScale - photoView.minScale)

            animateScaleAndCenter.withDuration((scaleWeight * DURATION).toLong())
                .start()
        } else {
            photoView.resetScaleAndCenter()
            doHidePreview()
        }
    }

    private fun doHidePreview() {
        photoView.alpha = 0F
        backPressedCallback.isEnabled = false
        onPreviewHideCallback()
        showHideAnimation()
    }

    private fun onAnimationEnd() {
        log.i("onAnimationEnd: $animationProgress")
        if (animationProgress < PROGRESS_CLOSE) {
            // 如果距离开始的动画小于1%，那么就认为关闭了
            previewPanel.isInvisible = true
            currentItemView?.translationZ = 0F
            currentItemView = null
        }
        if (animationProgress > PROGRESS_OPEN) {
            previewPanel.setBackgroundColor(BACKGROUND_COLOR)
            currentItemView?.isInvisible = true
            photoView.alpha = 1F
        }
    }

    private fun onAnimationUpdate(progress: Float) {
        this.animationProgress = progress
        currentItemView?.let { itemView ->
            val scale = ((anchor.scale - 1F) * progress) + 1F
            itemView.scaleX = scale
            itemView.scaleY = scale
            itemView.translationX = anchor.translationX * progress
            itemView.translationY = anchor.translationY * progress
        }
    }

    private fun updateAnchor(itemView: View) {
        log.i("updateAnchor")
        val tempLocal = IntArray(2)
        itemView.getLocationInWindow(tempLocal)
        val itemX = tempLocal[0]
        val itemY = tempLocal[1]
        previewPanel.getLocationInWindow(tempLocal)
        val previewX = tempLocal[0]
        val previewY = tempLocal[1]
        val fromX = itemX - previewX
        val fromY = itemY - previewY
        var fromWidth = itemView.width
        var fromHeight = itemView.height

        val previewWidth = previewPanel.width
        val previewHeight = previewPanel.height

        if (fromWidth < 1) {
            fromWidth = previewWidth
        }
        if (fromHeight < 1) {
            fromHeight = previewHeight
        }

        if (fromWidth < 1 || fromHeight < 1) {
            anchor.scale = 1F
            anchor.translationX = 0F
            anchor.translationY = 0F
            return
        }

        val scaleWidth = previewWidth * 1F / fromWidth
        val scaleHeight = previewHeight * 1F / fromHeight
        val scale = min(scaleWidth, scaleHeight)
        anchor.scale = scale

        val reverseX = ((previewWidth - fromWidth) / 2).toFloat()
        val reverseY = ((previewHeight - fromHeight) / 2).toFloat()

        anchor.translationX = (reverseX - fromX)
        anchor.translationY = (reverseY - fromY)

        log.i("updateAnchor: scale: $scale, previewWidth = ${previewWidth}, previewHeight = ${previewHeight}, fromWidth = ${fromWidth}, fromHeight = ${fromHeight}, translationX = ${anchor.translationX}, translationY = ${anchor.translationY}")
    }

    private class Anchor {
        var scale = 1F
        var translationX = 0F
        var translationY = 0F
    }

    private class ScaleImageViewAnimationListener(
        private val onInterruptedByUserCallback: () -> Unit = {},
        private val onInterruptedByNewAnimCallback: () -> Unit = {},
        private val onCompleteCallback: () -> Unit
    ) : SubsamplingScaleImageView.OnAnimationEventListener {
        override fun onComplete() {
            onCompleteCallback()
        }

        override fun onInterruptedByUser() {
            onInterruptedByUserCallback()
        }

        override fun onInterruptedByNewAnim() {
            onInterruptedByNewAnimCallback()
        }

    }

}