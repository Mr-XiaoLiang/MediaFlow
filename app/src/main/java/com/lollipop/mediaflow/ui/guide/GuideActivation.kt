package com.lollipop.mediaflow.ui.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 页面引导的开关状态：把 [GuideHost] 需要的 `active` 与 `onFinished` 成对给出。
 *
 * 「是否落盘」已由本对象按 `wasShown` 处理，页面不必再关心。
 */
@Stable
class GuideActivation internal constructor(

    /** 页面当前的展示意图，直接交给 [GuideHost]。 */
    val isActive: Boolean,

    /** 结束回调：内部已处理「一步都没展示过则不落盘」。 */
    internal val finish: (wasShown: Boolean) -> Unit,
)

/**
 * 「resume 时判定一次、结束时落盘」这段**通用机械流程**的收口。
 *
 * 页面只负责给出 [key] 与 [version]（这才是页面自己的决策）；至于判定时机、
 * 重复 resume 的守卫、以及「一步都没展示过就不落盘」的约定，全在此实现一次，
 * 避免各页面各写一遍而产生细微分歧。
 *
 * 判定刻意放在 `resume` 而不是组合期：用户在设置页点了「功能帮助」清空记录后返回页面，
 * `resume` 会再次触发判定，引导随即重新展示；若只在组合期判定，返回时组合并不重建，重置将不生效。
 *
 * @param key 本支引导的稳定标识（记录键），不随版本变化。
 * @param version 功能版本：内容有实质改动时 +1，用户即会再看一次。
 */
@Composable
fun rememberGuideActivation(key: String, version: Int): GuideActivation {
    val resumeSignal by rememberPageResumeSignal()
    var isActive by remember { mutableStateOf(false) }
    LaunchedEffect(resumeSignal, key, version) {
        // 尚未 resume 过：等到页面真正就绪再判定
        if (resumeSignal <= 0) {
            return@LaunchedEffect
        }
        // 正在展示：重复 resume（从别的页面返回、系统弹窗、分屏）不重启引导
        if (isActive) {
            return@LaunchedEffect
        }
        if (GuideSeenStore.isLatest(key, version)) {
            return@LaunchedEffect
        }
        isActive = true
    }
    return GuideActivation(
        isActive = isActive,
        finish = { wasShown ->
            isActive = false
            // 一步都没展示过（目标不在屏幕上被跳过）时不落盘，下次 resume 重新判定
            if (wasShown) {
                GuideSeenStore.markSeen(key, version)
            }
        },
    )
}
