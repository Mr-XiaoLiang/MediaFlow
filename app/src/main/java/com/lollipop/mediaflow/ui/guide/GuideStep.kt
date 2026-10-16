package com.lollipop.mediaflow.ui.guide

import androidx.compose.ui.unit.Dp

/**
 * 高亮洞的形状。
 *
 * 三种形状足以覆盖本项目需要提示的目标类型，因此不为圆角单独开参数：
 *
 * - [RoundedRect]：按钮 / 区块类目标，圆角取 [GuideOverlayStyle.HoleCornerRadius]；
 * - [Capsule]：胶囊类目标（如首页 Slogan 条），圆角取洞高的一半；
 * - [Circle]：圆形图标按钮，圆角取洞宽高中较小者的一半。
 */
enum class GuideHoleShape {

    RoundedRect,

    Capsule,

    Circle,
}

/**
 * 气泡相对高亮洞的摆放方向。
 *
 * 一律从洞的**外侧**摆放，方向空间不足时由实现自动换向（见 `GuideSpotlight`），
 * 因此这里给的只是**优先意图**，不是硬约束。
 */
enum class GuideBubbleSide {

    /** 由实现按四周剩余空间自动择向（默认，一般不必指定）。 */
    Auto,

    Below,

    Above,

    Start,

    End,
}

/**
 * 单步引导的**模板**（纯数据，不含任何逻辑与副作用）。
 *
 * ## 为什么是纯数据
 *
 * 引导的「该不该展示、展示什么」属于页面自己的决策，通用层只负责「怎么画、怎么测、怎么推进」。
 * 因此本类刻意不含回调、不含 `@Composable`、不引用 Android 资源体系——文案由页面在组合期
 * 用 `stringResource` 解析为 [String] 后传入，使模板可以在任意上下文构造与比对。
 *
 * ## 与目标的关系
 *
 * [target] 既可以是组件锚点（需与 `Modifier.guideAnchor(key)` 的键一致，找不到或不在屏幕上
 * 则**跳过该步**），也可以是按窗口比例模拟的区域（无需任何测量，必然可展示）。
 */
data class GuideStep(
    /** 高亮目标：组件锚点或模拟区域。 */
    val target: GuideTarget,
    /** 标题：一句话点明「这是什么」。 */
    val title: String,
    /** 说明：为什么它不直观、以及怎么用。 */
    val description: String,
    /** 高亮洞的形状。 */
    val holeShape: GuideHoleShape = GuideHoleShape.RoundedRect,
    /** 洞相对目标矩形的留白；留白让洞比目标「松一圈」，观感更从容。 */
    val holePadding: Dp = GuideOverlayStyle.HolePadding,
    /** 气泡的优先摆放方向。 */
    val bubbleSide: GuideBubbleSide = GuideBubbleSide.Auto,
)
