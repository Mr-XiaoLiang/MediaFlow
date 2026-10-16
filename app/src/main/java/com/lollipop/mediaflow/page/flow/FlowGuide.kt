package com.lollipop.mediaflow.page.flow

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
 * 视频播放页引导（**页面决策层**）。
 *
 * 只讲手势：视频区没有播放控件，全靠手势操作，而这在画面上没有任何提示。
 *
 * 与其他页面不同的是，本页**不锚定任何组件**——手势区不是某个具体控件，
 * 而是视频画面那一整片区域，因此用 [GuideRegion] 模拟范围即可（见 [GestureArea]）。
 */
object FlowGuide {

    /** 本支引导的稳定标识：记录键，不随版本变化。 */
    const val Key = "flow_guide"

    /** 功能版本：内容有实质改动时 +1。 */
    const val FeatureVersion = 1

    /**
     * 手势区的模拟范围（窗口比例）。
     *
     * 底部刻意收在 0.66：气泡要摆在洞的下方，若把洞铺满整屏，气泡只能压在画面中央，
     * 反而挡住手势区本身。留出的下方空间恰好也让「上下滑动调节倍速」的手势起点一目了然。
     */
    val GestureArea = GuideRegion(
        startFraction = 0.04F,
        topFraction = 0.08F,
        endFraction = 0.96F,
        bottomFraction = 0.66F,
    )
}

/**
 * 播放页引导宿主：判定与落盘交给 [rememberGuideActivation]，其余交给 [GuideHost]。
 *
 * @param content 播放页内容。宿主只在其外侧叠一层遮罩，不改变内容自身的布局。
 */
@Composable
fun FlowGuide(content: @Composable () -> Unit) {
    val activation = rememberGuideActivation(
        key = FlowGuide.Key,
        version = FlowGuide.FeatureVersion,
    )
    GuideHost(
        active = activation.isActive,
        steps = flowGuideSteps(),
        onFinished = activation.finish,
        content = content,
    )
}

/**
 * 播放页引导的步骤定义。
 *
 * 归档按钮**不做引导**：它是一次性点击就能看明白的显式控件，引导留给真正看不出来的手势。
 */
@Composable
private fun flowGuideSteps(): List<GuideStep> {
    return listOf(
        GuideStep(
            target = GuideTarget.Area(FlowGuide.GestureArea),
            title = stringResource(id = R.string.guide_flow_gesture_title),
            description = stringResource(id = R.string.guide_flow_gesture_desc),
            holeShape = GuideHoleShape.RoundedRect,
            bubbleSide = GuideBubbleSide.Below,
        )
    )
}
