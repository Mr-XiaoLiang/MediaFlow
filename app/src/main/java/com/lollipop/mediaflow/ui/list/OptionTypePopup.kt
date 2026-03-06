package com.lollipop.mediaflow.ui.list

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.PopupWindow
import androidx.core.graphics.drawable.toDrawable
import com.lollipop.mediaflow.data.MediaLayout
import com.lollipop.mediaflow.databinding.PopupDisplayTypeBinding

object OptionTypePopup {

    fun show(anchorView: View, onClick: (MediaLayout) -> Unit) {
        val context = anchorView.context

        val popupWindow = PopupWindow(context)

        val contentBinding = PopupDisplayTypeBinding.inflate(LayoutInflater.from(context))
        val popupView = contentBinding.root

        contentBinding.galleryButton.setOnClickListener {
            onClick(MediaLayout.Gallery)
            popupWindow.dismiss()
        }

        contentBinding.flowButton.setOnClickListener {
            onClick(MediaLayout.Flow)
            popupWindow.dismiss()
        }

        popupWindow.isFocusable = true;
        popupWindow.isOutsideTouchable = true;
        popupWindow.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        popupWindow.contentView = popupView

        // 1. 首先测量 PopupWindow 内容视图的宽高，确保计算准确
        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popupWidth = popupView.measuredWidth
        val popupHeight = popupView.measuredHeight
        popupWindow.width = popupWidth
        popupWindow.height = popupHeight

        // 2. 计算偏移量
        val xOff: Int = (anchorView.width - popupWidth) / 2
        val yOff: Int = (anchorView.height + popupHeight) / 2 * -1
        popupWindow.showAsDropDown(anchorView, xOff, yOff)
    }


}