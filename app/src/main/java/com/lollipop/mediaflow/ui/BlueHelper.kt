package com.lollipop.mediaflow.ui

import android.graphics.drawable.Drawable
import android.view.Window
import com.lollipop.mediaflow.R
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView

object BlueHelper {

    fun bind(window: Window, target: BlurTarget, vararg blurView: BlurView) {
        val radius = 20f;
        val context = target.context
        val overlayColor = context.resources.getColor(R.color.blur_overlay, context.theme)
        val windowBackground = window.decorView.background
        bind(
            target = target,
            windowBackground = windowBackground,
            overlayColor = overlayColor,
            radius = radius,
            blurView
        )
    }

    fun bind(
        target: BlurTarget,
        windowBackground: Drawable,
        overlayColor: Int,
        radius: Float,
        blurView: Array<out BlurView>
    ) {
        blurView.forEach {
            it.setupWith(target)
                .setFrameClearDrawable(windowBackground)
                .setBlurRadius(radius)
                .setOverlayColor(overlayColor)
        }
    }

}