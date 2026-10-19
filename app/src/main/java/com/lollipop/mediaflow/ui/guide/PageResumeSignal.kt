package com.lollipop.mediaflow.ui.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner

/**
 * 页面级 `onResume` 信号：每收到一次 `ON_RESUME` 递增一次。
 *
 * ## 用途
 *
 * 引导的「要不要展示」由页面在 **resume 就绪时判定一次**（而非组合期判定）。
 * 原因是设置页的「功能帮助」清空记录后，用户**返回**页面就会触发 `onResume`，
 * 此时重新判定即可让引导当场再次展示，形成完整体验闭环；
 * 若只在组合期判定一次，返回时组合并不重建，重置将不生效。
 *
 * ## 为什么不用 compose 生命周期库
 *
 * 本项目**没有**引入 `lifecycle-runtime-compose`。这里改为通过
 * [findViewTreeLifecycleOwner] 拿到宿主 `Lifecycle` 并自行观察，因此**不新增任何依赖**。
 *
 * ## 两个易漏的情形
 *
 * 1. **组合发生在 resume 之后**（页面内容延后进入组合、或从别的页面返回后重组）：
 *    此时 `ON_RESUME` 事件已经过去，观察者永远等不到 —— 因此初始值按「当前是否已 RESUMED」
 *    取 `1`，并把该次也算作一次信号，避免**漏判**。
 * 2. **页面反复 resume**（返回、系统弹窗、分屏）：信号会持续递增，由调用方（[GuideHost]）
 *    只认上升沿来防止重复启动引导。
 *
 * 返回值语义：`0` 表示尚未 resume 过；`>= 1` 表示至少就绪过一次。
 */
@Composable
fun rememberPageResumeSignal(): State<Int> {
    val view = LocalView.current
    val owner = remember(view) {
        view.findViewTreeLifecycleOwner()
    }
    // 组合期只读取生命周期状态（不写入 Compose 状态），因此不会引入额外的重组轮次
    val isResumedAlready = owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
    val signal = remember(owner) {
        mutableIntStateOf(if (isResumedAlready) 1 else 0)
    }
    DisposableEffect(owner) {
        if (owner == null) {
            return@DisposableEffect onDispose { }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                signal.intValue += 1
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
        }
    }
    return signal
}
