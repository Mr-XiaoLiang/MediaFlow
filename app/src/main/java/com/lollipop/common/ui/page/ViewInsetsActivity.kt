package com.lollipop.common.ui.page

import android.graphics.Rect
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * **View 分支**的 insets 实现层。
 *
 * 从指定根 View 采集 window insets（systemBars / displayCutout / systemGestures 逐边取 max），
 * 分发给两条既有通路：
 * - [GuidelineInsetsHelper]（ConstraintLayout 上的 Guideline 布局）；
 * - [InsetsFragment]（Fragment 通过 [InsetsFragment.Provider] 拉取 / 订阅）。
 *
 * 采集口径是 View 时代的既有约定；Compose 页面**不使用**本层，
 * 而是在 `BasicComposeActivity` 分支里直接用 Compose 自洽的 `WindowInsets`。
 */
abstract class ViewInsetsActivity : BasicPageActivity(), InsetsFragment.Provider {

    protected val insetsProviderHelper = InsetsFragment.ProviderHelper()

    private val guidelineInsetsGroup = GuidelineInsetsGroup()

    protected open val checkSystemBarsInsets = true
    protected open val checkDisplayCutoutInsets = true
    protected open val checkSystemGesturesInsets = false

    protected var insetsCache = Insets.NONE
        private set

    fun registerGuidelineInsetsListener(helper: GuidelineInsetsHelper) {
        guidelineInsetsGroup.register(helper)
    }

    fun unregisterGuidelineInsetsListener(helper: GuidelineInsetsHelper) {
        guidelineInsetsGroup.unregister(helper)
    }

    /**
     * 把根 View 的 window insets 接入本层。
     *
     * 注意：应在**布局内容承载 View**（`setContentView` 的 root）上调用；
     * Compose 页面不要调用本方法（会覆盖 `enableEdgeToEdge` 在旧系统上注册的 listener）。
     */
    protected fun initInsetsListener(rootView: View) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            insetsCache = findInsets(insets)
            insetsProviderHelper.updateInsets(
                insetsCache.left,
                insetsCache.top,
                insetsCache.right,
                insetsCache.bottom
            )
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

    /** 采集口径：三类 insets 逐边取最大值。 */
    protected abstract fun onWindowInsetsChanged(
        left: Int, top: Int, right: Int, bottom: Int
    )

    override fun getInsets(): Rect {
        return insetsProviderHelper.getInsets()
    }

    override fun registerInsetsListener(listener: InsetsFragment.InsetsListener) {
        insetsProviderHelper.registerInsetsListener(listener)
    }

    override fun unregisterInsetsListener(listener: InsetsFragment.InsetsListener) {
        insetsProviderHelper.unregisterInsetsListener(listener)
    }

    private fun findInsets(insets: WindowInsetsCompat): Insets {
        val systemBars = if (checkSystemBarsInsets) {
            insets.getInsets(WindowInsetsCompat.Type.systemBars())
        } else {
            Insets.NONE
        }
        val displayCutout = if (checkDisplayCutoutInsets) {
            insets.getInsets(WindowInsetsCompat.Type.displayCutout())
        } else {
            Insets.NONE
        }
        val systemGestures = if (checkSystemGesturesInsets) {
            insets.getInsets(WindowInsetsCompat.Type.systemGestures())
        } else {
            Insets.NONE
        }

        return Insets.of(
            max(systemBars.left, displayCutout.left, systemGestures.left),
            max(systemBars.top, displayCutout.top, systemGestures.top),
            max(systemBars.right, displayCutout.right, systemGestures.right),
            max(systemBars.bottom, displayCutout.bottom, systemGestures.bottom)
        )
    }

    private fun max(vararg values: Int): Int {
        var max = values[0]
        for (i in 1 until values.size) {
            if (values[i] > max) {
                max = values[i]
            }
        }
        return max
    }

    private fun updateGuidelineInsets(
        left: Int, top: Int, right: Int, bottom: Int
    ) {
        log.i("updateGuidelineInsets: $left, $top, $right, $bottom")
        guidelineInsetsGroup.updateGuidelineInsets(left, top, right, bottom)
    }

    protected class GuidelineInsetsGroup {

        private val insetsListener = mutableListOf<GuidelineInsetsHelper>()

        fun register(helper: GuidelineInsetsHelper) {
            insetsListener.add(helper)
        }

        fun unregister(helper: GuidelineInsetsHelper) {
            insetsListener.remove(helper)
        }

        fun updateGuidelineInsets(
            left: Int, top: Int, right: Int, bottom: Int
        ) {
            insetsListener.forEach { it.updateGuidelineInsets(left, top, right, bottom) }
        }

    }

}
