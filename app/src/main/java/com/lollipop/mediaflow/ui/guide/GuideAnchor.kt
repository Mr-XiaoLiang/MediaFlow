package com.lollipop.mediaflow.ui.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * 引导锚点注册表：记录「锚点键 → 目标在窗口中的矩形」。
 *
 * ## 为什么用窗口坐标
 *
 * 上报 [boundsInWindow]，而不是 `boundsInRoot`。后者依赖「遮罩恰好铺满 root」这一**隐式约定**：
 * 一旦页面结构变化（多包一层容器、加一处内边距），坐标就会**静默错位**——洞画在别处，
 * 而代码里没有任何一行提示出错。窗口坐标则与页面结构无关，只要遮罩铺满窗口就必然对齐。
 *
 * ## 生命周期
 *
 * 由 [GuideHost] 在**有引导待展示时**才提供（见 [LocalGuideAnchors]）；
 * 目标的 `DisposableEffect` 负责在自身离开组合时移除记录，避免残留过期矩形。
 *
 * 同一键在同一个页面内必须唯一，重复挂载时后上报者覆盖先者。
 */
@Stable
class GuideAnchorRegistry {

    private val rects = mutableStateMapOf<String, Rect>()

    /** 查询锚点矩形；目标尚未布局或已离开组合时返回 `null`。 */
    fun rectOf(key: String): Rect? {
        return rects[key]
    }

    internal fun update(key: String, rect: Rect) {
        rects[key] = rect
    }

    internal fun remove(key: String) {
        rects.remove(key)
    }
}

/**
 * 供子树读取的锚点注册表。
 *
 * 为 `null` 表示当前页面没有引导在等待——此时 [guideAnchor] 退化为**直接返回自身**，
 * 不挂任何修饰符、不产生任何测量回调。这是「不需要展示时不浪费资源」在锚点侧的实现。
 */
val LocalGuideAnchors = compositionLocalOf<GuideAnchorRegistry?> { null }

/**
 * 声明「本组件是某一步引导的目标组件」。
 *
 * 用法（目标组件侧只加这一个修饰符）：
 *
 * ```kotlin
 * Text(
 *     text = ...,
 *     modifier = Modifier.guideAnchor(HomeGuide.AnchorSlogan)
 * )
 * ```
 *
 * [key] 必须与 [GuideStep.anchorKey] 一致。目标被跳过、被条件隐藏或被滚出屏幕时，
 * 实现会依据矩形缺失或不在屏幕上把该步跳过，**不会**把遮罩指向空气。
 *
 * 注意本函数是 `@Composable`：它需要注册表上下文，并在目标离开组合时清理记录。
 */
@Composable
fun Modifier.guideAnchor(key: String): Modifier {
    val registry = LocalGuideAnchors.current ?: return this
    DisposableEffect(registry, key) {
        onDispose {
            registry.remove(key)
        }
    }
    return this.onGloballyPositioned { coordinates ->
        registry.update(key, coordinates.boundsInWindow())
    }
}
