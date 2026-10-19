package com.lollipop.mediaflow.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.ui.guide.GuideBubbleSide
import com.lollipop.mediaflow.ui.guide.GuideHoleShape
import com.lollipop.mediaflow.ui.guide.GuideHost
import com.lollipop.mediaflow.ui.guide.GuideRegion
import com.lollipop.mediaflow.ui.guide.GuideStep
import com.lollipop.mediaflow.ui.guide.GuideTarget
import com.lollipop.mediaflow.ui.guide.rememberGuideActivation

/**
 * 首页引导（**页面决策层**）。
 *
 * ## 职责
 *
 * 只回答两件事：**首页该不该展示引导**、**展示哪些步骤**。遮罩怎么画、目标怎么定位、
 * 步骤怎么推进全部由 `ui/guide` 的通用层负责，因此首页的逻辑不会渗进通用层，
 * 通用层也不会因为首页的特殊性而变复杂。
 *
 * ## 一支引导 = 一个记录键
 *
 * 本页的多步引导共用 [Key] 一条记录：只要展示过任意一步就算「看过」，避免出现
 * 「中途退出导致后半段被误标为已看」的错位。改动引导内容（文案或目标）时把 [FeatureVersion] +1，
 * 用户就会重新看到一次。
 */
object HomeGuide {

    /** 本支引导的稳定标识：记录键，不随版本变化。 */
    const val Key = "home_guide"

    /** 功能版本：内容有实质改动时 +1。 */
    const val FeatureVersion = 1

    /** 本页的锚点键，须与目标组件上 `Modifier.guideAnchor(key)` 的取值一致。 */
    object Anchors {

        /** 顶部悬浮 Slogan 胶囊。 */
        const val Slogan = "home_slogan"
    }

    /**
     * 模拟区域：用于没有具体组件可锚定的提示。
     *
     * 右侧竖带对应快速滚动的手势区。它不适合挂真实锚点：可命中的热区本身就是一条窄带，
     * 而它在 Pager 的相邻页上也会被组合，同一个键会被多页同时上报。
     * 这里按窗口比例模拟大致范围即可——目的是告诉用户「这一条可以长按拖动」，
     * 而不是精确描出热区。
     */
    object Regions {

        val FastScroller = GuideRegion.endBand(
            startFraction = 0.84F,
            topFraction = 0.12F,
            bottomFraction = 0.88F,
        )
    }
}

/**
 * 首页引导宿主：把判定与落盘交给 [rememberGuideActivation]，其余交给 [GuideHost]。
 *
 * @param content 首页内容。宿主只在其外侧叠一层遮罩，不改变内容自身的布局。
 */
@Composable
fun HomeGuide(content: @Composable () -> Unit) {
    val activation = rememberGuideActivation(
        key = HomeGuide.Key,
        version = HomeGuide.FeatureVersion,
    )
    GuideHost(
        active = activation.isActive,
        steps = homeGuideSteps(),
        onFinished = activation.finish,
        content = content,
    )
}

/**
 * 首页引导的步骤定义。
 *
 * 只挑**不直观**的交互来讲：
 *
 * 1. Slogan 胶囊的点击是「展开菜单」，长按才是「切换隐私模式」——从表面上完全看不出来；
 * 2. 屏幕右边缘那一条是快速滚动的手势区，同样没有任何视觉提示。
 *
 * 两者的定位方式不同（组件锚点 / 模拟区域），这正是 [GuideTarget] 区分两态的意义。
 */
@Composable
private fun homeGuideSteps(): List<GuideStep> {
    return listOf(
        GuideStep(
            target = GuideTarget.Anchor(HomeGuide.Anchors.Slogan),
            title = stringResource(id = R.string.guide_home_slogan_title),
            description = stringResource(id = R.string.guide_home_slogan_desc),
            holeShape = GuideHoleShape.Capsule,
            bubbleSide = GuideBubbleSide.Below,
        ),
        GuideStep(
            target = GuideTarget.Area(HomeGuide.Regions.FastScroller),
            title = stringResource(id = R.string.guide_home_scroller_title),
            description = stringResource(id = R.string.guide_home_scroller_desc),
            holeShape = GuideHoleShape.RoundedRect,
            bubbleSide = GuideBubbleSide.Start,
        ),
    )
}
