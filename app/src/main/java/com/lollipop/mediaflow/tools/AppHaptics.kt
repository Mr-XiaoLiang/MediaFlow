package com.lollipop.mediaflow.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * 触觉反馈（震动）的统一入口。
 *
 * ## 使用准则（务必遵守）
 *
 * 震动**只**用于「**无其它反馈的模式 / 手势进入确认**」：某个持续手势或模式**开始的那一刻**，
 * 界面上没有任何可见 / 可听的变化告诉用户「已进入」，才给一次震动提示。
 *
 * 反之，凡是有直接反馈的交互一律**不震**：
 * - 滑块跟随、进度更新、OSD 提示、状态高亮；
 * - 动作结果（播放 / 暂停、跳转等）；
 * - 手势「结束」；
 * - 离散点击。
 *
 * ## 统一收口
 *
 * 所有震动都必须经由此门面调用（不要直接调用 `performHapticFeedback`），以便：
 * - 统一受偏好 [Preferences.isHapticFeedbackEnable] 控制，关闭即 no-op；
 * - 保证同类交互使用同一种 [HapticFeedbackType]，体验一致。
 *
 * 目前全 App 仅有一个合规场景：首页快速滚动条的**长按激活**（长按瞬间界面无任何变化）。
 * 若将来出现同类场景，一律复用 [onGestureModeEntered]，不要新增语义。
 */
class AppHaptics internal constructor(private val hapticFeedback: HapticFeedback) {

    /**
     * 提示「一段**无视觉反馈**的手势 / 模式已开始」。
     *
     * 这是当前唯一合规的震动语义。调用点必须满足：触发瞬间界面没有任何可见变化。
     */
    fun onGestureModeEntered() {
        if (!Preferences.isHapticFeedbackEnable.get()) {
            return
        }
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

/** 取得当前组合环境下的 [AppHaptics]（内部持有 [LocalHapticFeedback]）。 */
@Composable
fun rememberAppHaptics(): AppHaptics {
    val hapticFeedback = LocalHapticFeedback.current
    return remember(hapticFeedback) {
        AppHaptics(hapticFeedback)
    }
}
