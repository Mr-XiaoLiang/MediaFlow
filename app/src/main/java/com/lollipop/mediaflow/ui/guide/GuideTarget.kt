package com.lollipop.mediaflow.ui.guide

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize

/**
 * 一步引导的「高亮目标」。
 *
 * 两种来源的差别在于**是否需要真实组件坐标**：
 *
 * - [Anchor]：高亮某个组件的实际位置，需要目标挂 `Modifier.guideAnchor(key)`，
 *   适合「有明确组件、且必须精确框住它」的提示；
 * - [Area]：不锚定任何组件，只按窗口比例圈出一块范围，适合「这一片是手势区」这类
 *   **没有具体组件、也不必精确**的提示。
 *
 * 显式区分为两态而不是用「可空锚点键 + 兜底区域」：后者把两种语义压进一个可空字段，
 * 调用方无法一眼看出这一步到底靠什么定位。
 */
sealed interface GuideTarget {

    /** 组件锚点：高亮组件实际位置；目标不存在或不在屏幕上时该步被跳过。 */
    data class Anchor(val key: String) : GuideTarget

    /**
     * 模拟区域：按窗口比例圈出范围。
     *
     * 区域由窗口尺寸推导，**天然落在屏幕内**，因此不存在「被跳过」的情形——
     * 这正是它适合告知「大概范围」的原因。
     */
    data class Area(val region: GuideRegion) : GuideTarget
}

/**
 * 以窗口尺寸的**比例**描述的区域：四边取值 `0F ~ 1F`，0 表示窗口左 / 上边，1 表示右 / 下边。
 *
 * 用比例而不是 dp：这类提示只表达「大概的范围」，比例不依赖具体机型尺寸，
 * 也不会因为日后调整尺寸常量而失去对齐。
 */
data class GuideRegion(
    val startFraction: Float,
    val topFraction: Float,
    val endFraction: Float,
    val bottomFraction: Float,
) {

    /** 换算为窗口坐标下的矩形。 */
    fun toRect(windowSize: IntSize): Rect {
        return Rect(
            left = windowSize.width * startFraction,
            top = windowSize.height * topFraction,
            right = windowSize.width * endFraction,
            bottom = windowSize.height * bottomFraction,
        )
    }

    /** 四边同时向内收 [fraction]，用于需要避开屏幕边缘的「整片区域」提示。 */
    fun inset(fraction: Float): GuideRegion {
        return GuideRegion(
            startFraction = startFraction + fraction,
            topFraction = topFraction + fraction,
            endFraction = endFraction - fraction,
            bottomFraction = bottomFraction - fraction,
        )
    }

    companion object {

        /** 整窗区域。 */
        val Full = GuideRegion(
            startFraction = 0F,
            topFraction = 0F,
            endFraction = 1F,
            bottomFraction = 1F,
        )

        /** 右侧竖带：用于「屏幕右边缘这一条是手势区」这类提示。 */
        fun endBand(startFraction: Float, topFraction: Float, bottomFraction: Float): GuideRegion {
            return GuideRegion(
                startFraction = startFraction,
                topFraction = topFraction,
                endFraction = 1F,
                bottomFraction = bottomFraction,
            )
        }
    }
}
