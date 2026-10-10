package com.lollipop.mediaflow.page.flow.compose.gesture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** 双指缩放下限（对齐 `FlowPlayerGestureHost.SCALE_MIN`）。 */
const val FLOW_SCALE_MIN = 0.8F

/** 双指缩放上限（对齐 `FlowPlayerGestureHost.SCALE_MAX`）。 */
const val FLOW_SCALE_MAX = 6F

/**
 * 缩放 / 平移状态。
 *
 * 与 View 版的关键差异：不再持有 [android.graphics.Matrix]，而是保存 `scale + offset + origin`
 * 三件套，由 [Modifier.flowGestureTransform] 用 `graphicsLayer` 渲染。
 * 好处是滑块、OSD 等与视频同处一个变换容器时，无需再各自换算 Matrix。
 */
@Stable
class FlowGestureState {

    /** 当前缩放（1F 为原始大小）。 */
    var scale by mutableFloatStateOf(1F)

    /** 当前平移（像素）。 */
    var offset by mutableStateOf(Offset.Zero)

    /** 缩放原点 X（归一化 0..1，对齐 `Matrix.postScale(fx, fy, focusX, focusY)` 的焦点）。 */
    var originX by mutableFloatStateOf(0.5F)

    /** 缩放原点 Y（归一化 0..1）。 */
    var originY by mutableFloatStateOf(0.5F)

    /** 供 `graphicsLayer` 使用的缩放原点。 */
    val transformOrigin: TransformOrigin
        get() {
            return TransformOrigin(originX, originY)
        }

    /** 是否被用户变换过。 */
    val isTransformed: Boolean
        get() {
            return scale != 1F || offset != Offset.Zero
        }

    /** 复位（切页 / 转屏 / 三击）。 */
    fun reset() {
        scale = 1F
        offset = Offset.Zero
        originX = 0.5F
        originY = 0.5F
    }
}

/**
 * 单页手势语义回调。
 *
 * 只有「捕获开始 / 移动 / 释放」，**不含任何业务语义**：效果由观察者（字幕、进度、倍速…）订阅，
 * 与 View 版 `OnFlowTouchListener` 的分工一致。
 */
interface FlowGestureListener {

    /** 长按超时或横向位移达标，进入捕获态。 */
    fun onSingleCapture(size: IntSize, touchDown: Offset, current: Offset)

    /** 捕获态下的每帧移动。 */
    fun onSingleMove(size: IntSize, touchDown: Offset, current: Offset)

    /** 捕获结束（抬手 / 取消）。 */
    fun onTouchRelease()
}

@Composable
fun rememberFlowGestureState(): FlowGestureState {
    return remember { FlowGestureState() }
}

/**
 * Compose 手势事件源：语义逐条复刻
 * [com.lollipop.common.ui.view.FlowPlayerGestureHost]。
 *
 * 对齐要点：
 * - **长按超时**：`ViewConfiguration.getLongPressTimeout() * 0.7`（同 View 版的"爽快感"调整）；
 * - **单指 slop 判定**：横向超过 `touchSlop * 2` 才捕获，纵向超过 `touchSlop` 则放弃（让位给
 *   `VerticalPager` 的翻页）；
 * - **双指接管**：捕获态下多指落下即取消；仍处 Pending 时第二指落下则进入双指缩放；
 * - **穿透区域**：命中 [penetrateBounds]（例如侧栏 / 滑块）直接放弃，不消费事件；
 * - **消费**：仅在捕获 / 双指期间消费指针事件，从而阻止 `VerticalPager` 的纵向滚动（等价 View 版
 *   的 `requestDisallowInterceptTouchEvent`）。
 *
 * ⚠️ 手势与缩放的观感需真机验证（计划已注明）。
 */
fun Modifier.flowGesture(
    state: FlowGestureState,
    listener: FlowGestureListener?,
    enabled: Boolean = true,
    penetrateBounds: () -> List<Rect> = { emptyList() }
): Modifier {
    if (!enabled || listener == null) {
        return this
    }
    return pointerInput(state, listener, enabled) {
        val slop = viewConfiguration.touchSlop
        val longPressTimeout = (android.view.ViewConfiguration.getLongPressTimeout() * 0.7F).toLong()
        val viewSize = size
        val penetrate = penetrateBounds()

        var mode = TouchMode.Pending
        var down = Offset.Zero
        var current = Offset.Zero
        var twoFinger = TwoFinger.Zero
        var longPressJob: Job? = null

        fun captureSingle() {
            if (mode != TouchMode.Pending) {
                return
            }
            mode = TouchMode.Single
            listener.onSingleCapture(viewSize, down, current)
        }

        fun cancelTouch() {
            if (mode == TouchMode.Single || mode == TouchMode.Double) {
                listener.onTouchRelease()
            }
            mode = TouchMode.Cancel
            longPressJob?.cancel()
            longPressJob = null
        }

        coroutineScope {
            awaitPointerEventScope {
                var pressedCount = 0
                while (true) {
                    val event = awaitPointerEvent()
                    val nowPressed = event.changes.count { it.pressed }
                    val anchor = event.changes.firstOrNull { it.pressed } ?: event.changes.firstOrNull()
                    if (anchor != null) {
                        current = anchor.position
                    }

                    if (pressedCount == 0 && nowPressed > 0) {
                        // 新手势起点
                        down = current
                        mode = TouchMode.Pending
                        if (penetrate.any { it.contains(down) }) {
                            mode = TouchMode.Cancel
                        } else {
                            longPressJob?.cancel()
                            longPressJob = launch {
                                delay(longPressTimeout)
                                if (mode == TouchMode.Pending) {
                                    captureSingle()
                                }
                            }
                        }
                    } else if (nowPressed > pressedCount) {
                        // 追加手指
                        if (mode == TouchMode.Single) {
                            cancelTouch()
                        } else if (mode == TouchMode.Pending && nowPressed >= 2) {
                            mode = TouchMode.Double
                            twoFinger = twoFingerOf(event)
                        }
                    } else if (nowPressed < pressedCount) {
                        if (mode == TouchMode.Double && nowPressed < 2) {
                            cancelTouch()
                        }
                    }

                    when (mode) {
                        TouchMode.Double -> {
                            if (nowPressed >= 2) {
                                val now = twoFingerOf(event)
                                applyDoubleTransform(state, twoFinger, now, viewSize)
                                twoFinger = now
                                event.changes.forEach { it.consume() }
                            }
                        }

                        TouchMode.Single -> {
                            listener.onSingleMove(viewSize, down, current)
                            event.changes.forEach { it.consume() }
                        }

                        TouchMode.Pending -> {
                            val dx = abs(current.x - down.x)
                            val dy = abs(current.y - down.y)
                            if (dx > slop * 2) {
                                captureSingle()
                                listener.onSingleMove(viewSize, down, current)
                                event.changes.forEach { it.consume() }
                            } else if (dy > slop) {
                                // 纵向优先：让位给 VerticalPager 翻页
                                cancelTouch()
                            }
                        }

                        TouchMode.Cancel -> {}
                    }

                    if (nowPressed == 0) {
                        cancelTouch()
                        pressedCount = 0
                    } else {
                        pressedCount = nowPressed
                    }
                }
            }
        }
    }
}

/**
 * 把 [FlowGestureState] 的缩放 / 平移应用到内容，替代旧 `MatrixFrameLayout` + [android.graphics.Matrix]。
 *
 * 滑块、OSD 与视频应放在同一个带本修饰符的容器内，从而共享同一套变换。
 */
fun Modifier.flowGestureTransform(state: FlowGestureState): Modifier {
    return graphicsLayer {
        scaleX = state.scale
        scaleY = state.scale
        translationX = state.offset.x
        translationY = state.offset.y
        transformOrigin = state.transformOrigin
    }
}

/** 双指度量：距离与焦点。 */
private class TwoFinger(val a: Offset, val b: Offset) {

    val distance: Float
        get() {
            return (a - b).getDistance()
        }

    val focus: Offset
        get() {
            return (a + b) / 2F
        }

    companion object {
        val Zero = TwoFinger(Offset.Zero, Offset.Zero)
    }
}

private fun twoFingerOf(event: androidx.compose.ui.input.pointer.PointerEvent): TwoFinger {
    val pressed = event.changes.filter { it.pressed }
    return TwoFinger(
        a = pressed.getOrNull(0)?.position ?: Offset.Zero,
        b = pressed.getOrNull(1)?.position ?: Offset.Zero
    )
}

/**
 * 双指变换：先按距离比例缩放（围绕当前焦点），再跟随焦点位移平移。
 *
 * 语义等价 View 版的 `postScale(factor, factor, focusX, focusY)` + `postTranslate(Δfocus)`，
 * 但用 `graphicsLayer` 的 `scale + transformOrigin + translation` 表达。
 */
private fun applyDoubleTransform(
    state: FlowGestureState,
    last: TwoFinger,
    now: TwoFinger,
    size: IntSize
) {
    if (last.distance <= 0F || now.distance <= 0F) {
        return
    }
    val factor = now.distance / last.distance
    state.scale = (state.scale * factor).coerceIn(FLOW_SCALE_MIN, FLOW_SCALE_MAX)
    if (size.width > 0 && size.height > 0) {
        state.originX = (now.focus.x / size.width).coerceIn(0F, 1F)
        state.originY = (now.focus.y / size.height).coerceIn(0F, 1F)
    }
    state.offset += now.focus - last.focus
}

/** 手势状态机（对齐 View 版 `TouchMode`）。 */
private enum class TouchMode {
    Pending,
    Single,
    Double,
    Cancel
}
