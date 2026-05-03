package com.lollipop.mediaflow.ui

import android.view.View
import androidx.core.view.isVisible

open class VisibleFilter(val targetView: View) {

    var onChangedCallback: (() -> Unit)? = null

    private val pairs = mutableListOf<Pair>()

    val base = pair(true)

    val isVisible: Boolean
        get() {
            return targetView.isVisible
        }

    private fun onVisibleChanged() {
        var isVisible = true
        for (pair in pairs) {
            // 如果有一个不可见，则整体不可见
            if (!pair.isVisible) {
                isVisible = false
                break
            }
        }
        this.targetView.isVisible = isVisible
        onChangedCallback?.invoke()
    }

    fun pair(defaultVisible: Boolean = false): Pair {
        val pair = Pair(this, defaultVisible)
        pairs.add(pair)
        return pair
    }

    class Pair(
        private val filter: VisibleFilter,
        initialVisible: Boolean = false
    ) {

        var isVisible: Boolean = initialVisible
            private set

        fun setVisible(visible: Boolean) {
            isVisible = visible
            filter.onVisibleChanged()
        }

    }

}

open class PreferenceVisibleFilter(
    targetView: View,
    preferenceValue: Boolean = true
) : VisibleFilter(targetView) {
    val preference = pair(preferenceValue)
}

sealed class VisibleFilterGroup(val targetView: View) {

    protected val filters = mutableListOf<VisibleFilter>()

    val isVisible: Boolean
        get() {
            return targetView.isVisible
        }

    fun register(filter: VisibleFilter) {
        filters.add(filter)
        filter.onChangedCallback = ::onVisibleChanged
    }

    fun unregister(filter: VisibleFilter) {
        filters.remove(filter)
        filter.onChangedCallback = null
    }

    protected abstract fun getVisible(): Boolean

    protected fun onVisibleChanged() {
        targetView.isVisible = getVisible()
    }

    class And(targetView: View) : VisibleFilterGroup(targetView) {
        override fun getVisible(): Boolean {
            return filters.all { it.isVisible }
        }
    }

    class Or(targetView: View) : VisibleFilterGroup(targetView) {
        override fun getVisible(): Boolean {
            return filters.any { it.isVisible }
        }
    }

}
