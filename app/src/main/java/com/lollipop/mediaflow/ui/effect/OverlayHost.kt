package com.lollipop.mediaflow.ui.effect

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * 页面级浮层宿主状态。
 *
 * 供子组件把浮层「提升」到页面根渲染——浮层与页面内容处于**同一 Window**，
 * 因此可以使用 Haze 模糊（跨 Window 的 `Popup` / `Dialog` 做不到）。
 *
 * 同一时刻只保留一个浮层，新的会替换旧的；具体遮罩与消失逻辑由浮层内容自己实现。
 */
class OverlayHostState {

    private var content by mutableStateOf<(@Composable () -> Unit)?>(null)

    /** 当前浮层内容（无浮层时为 `null`）。 */
    internal val current: (@Composable () -> Unit)?
        get() = content

    /** 显示浮层。 */
    fun show(content: @Composable () -> Unit) {
        this.content = content
    }

    /** 关闭浮层。 */
    fun dismiss() {
        content = null
    }
}

/** 供子树读取的浮层宿主；未提供时为 `null`（此时调用方应回退到自身的降级实现）。 */
val LocalOverlayHost = compositionLocalOf<OverlayHostState?> { null }

/**
 * 页面级浮层宿主：把 [content] 与「浮层层」叠在同一个 `Box` 中。
 *
 * 浮层画在内容**之上**，但仍在同一 Window 内，因此可与页面内容共享 `HazeState`。
 * 一般由页面根（[com.lollipop.mediaflow.ui.BasicComposeActivity]）包一次即可。
 */
@Composable
fun OverlayHost(content: @Composable () -> Unit) {
    val host = remember { OverlayHostState() }
    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalOverlayHost provides host) {
            content()
        }
        // 浮层：最新的状态驱动，画在内容之上
        host.current?.invoke()
    }
}
