package com.lollipop.mediaflow.page.flow.video

import com.lollipop.mediaflow.tools.DisplayFormater

class ProgressOsdHelper(
    val onVisibilityChange: (progress: Boolean) -> Unit,
    val onProgressChange: (progress: String) -> Unit,
) {

    private var progressVisibleCount = 0
    private var totalProgress = 0L

    var isEnable = true
        set(value) {
            field = value
            updateVisibility()
        }

    fun setTotalProgress(totalProgress: Long) {
        this.totalProgress = totalProgress
    }

    fun reset() {
        progressVisibleCount = 0
        onProgressChange("")
        updateVisibility()
    }

    private fun updateVisibility() {
        onVisibilityChange(progressVisibleCount > 0 && isEnable)
        if (!isEnable) {
            onProgressChange("")
        }
    }

    fun showProgress() {
        progressVisibleCount++
        updateVisibility()
    }

    fun hideProgress() {
        progressVisibleCount--
        if (progressVisibleCount < 1) {
            onProgressChange("")
        }
        if (progressVisibleCount < 0) {
            progressVisibleCount = 0
        }
        updateVisibility()
    }

    fun setProgress(progress: Long) {
        if (!isEnable) {
            return
        }
        if (progressVisibleCount < 1) {
            return
        }
        onProgressChange(DisplayFormater.formatTime(progress, totalProgress))
    }

}