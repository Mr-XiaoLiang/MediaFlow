package com.lollipop.common.ui.page

/**
 * **View 分支**的页面基类（沿用原名，兼容既有页面引用）。
 *
 * 与 Compose 分支的差别只有一处：系统栏的默认策略沿用 View 页面的老行为
 * —— 竖屏显示系统栏、横屏隐藏系统栏。
 *
 * 其余能力（edge-to-edge、insets 采集、朝向检测）分别来自
 * [BasicPageActivity] 与 [ViewInsetsActivity]。
 */
abstract class CustomOrientationActivity : ViewInsetsActivity() {

    override fun onOrientationChanged(orientation: PageOrientation) {
        when (orientation) {
            PageOrientation.PORTRAIT -> showSystemUI()
            PageOrientation.LANDSCAPE -> hideSystemUI()
        }
    }

}
