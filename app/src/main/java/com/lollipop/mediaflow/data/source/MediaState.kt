package com.lollipop.mediaflow.data.source

/**
 * 来源读模型 / 页面数据状态。
 *
 * 关键在于把「未加载过」与「已加载但无内容」分成两个独立对象，避免二者都表现为「空列表」
 * 而被 UI 混为一谈：
 * - [Unloaded]：尚未产出任何一次结果（初始态）；
 * - [Empty]：已加载过一次，但（在当前范围 / 筛选 / 排序下）没有内容；
 * - [Content]：有内容。
 *
 * 每个来源的读模型 [MediaView] 暴露自身的 [MediaState]；展示来源组 `MediaSource`
 * 再把名下各来源的状态聚合为页面级 [MediaState]，UI 只读聚合结果，无需关心来源数量。
 */
sealed interface MediaState {

    /** 未加载过：来源刚构建、尚未产出任何一次结果。 */
    object Unloaded : MediaState

    /** 已加载但无内容。 */
    object Empty : MediaState

    /** 有内容。 */
    object Content : MediaState
}
