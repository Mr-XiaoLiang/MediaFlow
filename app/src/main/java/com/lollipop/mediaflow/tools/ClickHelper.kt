package com.lollipop.mediaflow.tools

import android.view.View

class ClickHelper(
    private val keepTimeMs: Long = 300,
    private val onClick: (Int) -> Unit
) : View.OnClickListener {
    private var lastClickTime: Long = 0
    private var clickCount = 0

    private val invokeTask = task {
        onClick.invoke(clickCount)
    }

    fun reset() {
        clickCount = 0
        lastClickTime = 0
    }

    override fun onClick(v: View?) {
        val currentTime = System.currentTimeMillis()
        invokeTask.cancel()
        if ((currentTime - lastClickTime) < keepTimeMs) {
            clickCount++
        } else {
            clickCount = 1
        }
        lastClickTime = currentTime
        invokeTask.delay(keepTimeMs)
    }

}