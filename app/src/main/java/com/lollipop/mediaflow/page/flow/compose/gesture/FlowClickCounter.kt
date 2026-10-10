package com.lollipop.mediaflow.page.flow.compose.gesture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 连续点击计数器（纯 Compose 版，语义复刻 [com.lollipop.common.tools.ClickHelper]）。
 *
 * 规则：两次点击间隔小于 [keepTimeMs] 则累加计数，静默 [keepTimeMs] 后回调总次数。
 * 因此页面可以按 `1 / 2 / 3` 分别处理「显隐控件 / 播放暂停 / 复位缩放」。
 *
 * 与 View 版的差异：View 版是 `View.OnClickListener`（挂在 `PlayerView` 上），这里改为
 * 显式调用 [FlowClickCounter.click]（由手势层的 tap 检测触发），并支持 [FlowClickCounter.reset]
 * 在进入触摸 seek / 倍速模式时丢弃未结算的点击（对齐旧实现里 `clickHelper.reset()` 的时机）。
 */
@Stable
class FlowClickCounter internal constructor(
    private val keepTimeMs: Long,
    private val scope: CoroutineScope
) {

    private var clickCount = 0
    private var pendingJob: Job? = null
    private var onResult: (Int) -> Unit = {}

    internal fun bind(block: (Int) -> Unit) {
        onResult = block
    }

    /** 记录一次点击（应只在「非长按、非触摸 seek」的轻触情况下调用）。 */
    fun click() {
        pendingJob?.cancel()
        clickCount++
        pendingJob = scope.launch {
            delay(keepTimeMs)
            val count = clickCount
            clickCount = 0
            pendingJob = null
            onResult(count)
        }
    }

    /** 丢弃未结算的点击（进入触摸手势 / 切页时调用）。 */
    fun reset() {
        pendingJob?.cancel()
        pendingJob = null
        clickCount = 0
    }
}

/**
 * 记住一个 [FlowClickCounter]。
 *
 * @param keepTimeMs 连击判定与结算延迟（默认 300ms，与 View 版一致）。
 * @param onClick 结算回调，参数为累计点击次数（1 / 2 / 3 …）。
 */
@Composable
fun rememberFlowClickCounter(
    keepTimeMs: Long = 300L,
    onClick: (Int) -> Unit
): FlowClickCounter {
    val scope = rememberCoroutineScope()
    val currentOnClick by rememberUpdatedState(onClick)
    val counter = remember(keepTimeMs, scope) {
        FlowClickCounter(keepTimeMs = keepTimeMs, scope = scope)
    }
    SideEffect {
        counter.bind { count -> currentOnClick(count) }
    }
    return counter
}
