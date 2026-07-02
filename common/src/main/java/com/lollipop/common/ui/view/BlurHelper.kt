package com.lollipop.common.ui.view

import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import com.lollipop.common.R
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView

class BlurHelper(
    private val radius: Float,
    private val overlayColorId: Int
) {

    companion object {

        val isEnable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        fun create(
            radius: Float = 6F,
            overlayColorId: Int = R.color.blur_overlay
        ): BlurHelper {
            return BlurHelper(radius, overlayColorId)
        }

        private fun bindBlurTarget(target: View): BlurTarget? {
            if (!isEnable) {
                return null
            }
            // 找不到容器就放弃
            val parent = target.parent
            // 如果找到了容器就使用
            if (parent is ViewGroup) {
                // 如果当前的view不是容器的子view，就放弃
                val index = parent.indexOfChild(target)
                if (index == -1) {
                    return null
                }
                // 记录原本的 layoutParams
                val layoutParams = target.layoutParams
                // 创建Blur的中间插件
                val blurGroup = BlurTarget(target.context)
                // 将目标View分离
                parent.removeView(target)
                // 插入 Blur
                parent.addView(blurGroup, index, layoutParams)
                // 将目标View嫁接到Blur上
                blurGroup.addView(
                    target,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                // 返回一个绑定成功Blur的对象
                return blurGroup
            }
            return null
        }

    }

    private var blurTarget: BlurTarget? = null

    fun bind(target: View) {
        blurTarget = bindBlurTarget(target)
    }

    fun update(window: Window, vararg blurView: BlurView) {
        val target = blurTarget
        if (!isEnable || target == null) {
            blurView.forEach {
                it.setBlurEnabled(false)
            }
            return
        }

        val context = target.context
        val overlayColor = context.resources.getColor(overlayColorId, context.theme)
        val windowBackground = window.decorView.background
        updateBlurView(
            target = target,
            windowBackground = windowBackground,
            overlayColor = overlayColor,
            blurView
        )
    }

    private fun updateBlurView(
        target: BlurTarget,
        windowBackground: Drawable,
        overlayColor: Int,
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