package com.lollipop.mediaflow.ui.gesture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

// TODO(diag): 临时诊断日志，「拖拽条手势不生效」定位后删除。
// 独立标签 + Log.e，保证不受 LLog.isDebug 门禁影响。
private const val DiagTag = "GestureDiag"

private fun diag(msg: String) {
    android.util.Log.e(DiagTag, msg)
}

/**
 * 手势目标：向 [GestureDispatchHost] 登记「自己的命中区 + 优先级」，由容器统一分发。
 *
 * ## 为什么需要它（而不是让浮层自己挂 `pointerInput`）
 *
 * Compose 的命中测试只把事件交给**最上层带指针输入的兄弟节点**，
 * `PointerInputModifierNode.sharePointerInputWithSiblings()` 默认 `false` 即「不允许其下方的兄弟
 * 响应事件」。因此一个叠在内容之上的浮层（侧边拖拽条之类）只要挂了 `pointerInput`，**下层兄弟
 * 就永远收不到事件**——哪怕该浮层「从不消费事件」也没用（事件根本没进下层兄弟的命中路径）。
 *
 * 正确做法是把手势识别搬到**根容器**：祖先节点本来就在子节点的命中路径上，于是
 * ① 子节点照常处理点击；② 容器只在「判定接管」后才 `consume()`，从而在滑动时把事件抢过来。
 * 这与视频页 [`Modifier.flowGesture`]（`penetrateBounds` + `consumedByChild` + 仅捕获期消费）
 * 是同一套思想，这里把它抽成可复用的容器。
 *
 * 后续新增手势组件（长按预览、区域缩放、多指手势…）只需实现本接口并登记，无需改动布局层级。
 */
@Stable
interface GestureTarget {

    /**
     * 命中范围（**容器局部坐标**，px）。返回 `null` 表示当前不参与分发
     * （例如条目数不足以显示拖拽条时）。
     */
    fun bounds(container: IntSize): Rect?

    /**
     * 优先级：数值越大越先拿到手势。
     *
     * 子节点（按钮 / 卡片 / 滑块）视作优先级 `0`：
     * - `priority <= 0` 的目标，在「子节点已消费按下事件」时一律让位（与视频页
     *   `consumedByChild → cancel` 同义）；
     * - `priority > 0` 的目标仍可竞争 —— 例如侧边拖拽条要能从卡片上起手拖动。
     *
     * 同优先级时，**后登记者**优先。
     */
    val priority: Int get() = 0

    /**
     * 本次手势是否由本目标接管（例如「纵向位移超过 `touchSlop`」）。
     *
     * 只有返回 `true` 之后容器才会开始消费事件，因此**单击永远不会被拦走**：轻触会照常
     * 落到下层子节点（按钮 / 卡片）上。
     */
    fun shouldCapture(down: Offset, current: Offset, slop: Float): Boolean

    /** 接管开始。 */
    fun onCapture(down: Offset, current: Offset) {}

    /** 接管期间的每帧移动。 */
    fun onMove(current: Offset) {}

    /** 接管结束（抬手 / 取消）。 */
    fun onRelease() {}
}

/**
 * 手势分发状态：持有已登记的目标，并按「位置 + 优先级」选出接管者。
 *
 * 由 [GestureDispatchHost] 创建（[rememberGestureDispatchState]），或在需要显式持有目标时自行创建。
 */
@Stable
class GestureDispatchState internal constructor() {

    private val targets = mutableListOf<GestureTarget>()

    /** 容器自身尺寸（px），由 [Modifier.gestureDispatch] 维护；供 [GestureTarget.bounds] 换算。 */
    internal var containerSize: IntSize = IntSize.Zero

    /** 同步登记表（[GestureDispatchHost] 在每次组合后调用）。 */
    internal fun setTargets(list: List<GestureTarget>) {
        targets.clear()
        targets.addAll(list)
    }

    /**
     * 按「位置 + 优先级」选出接管者。
     *
     * @param consumedByChild 子节点已在 Main pass 消费了按下事件（按钮 / 卡片 / 滑块已认领）。
     * @return 接管者；`null` 表示本次手势全部交给子节点，容器不介入。
     */
    internal fun resolve(
        down: Offset,
        consumedByChild: Boolean
    ): GestureTarget? {
        // 容器尺寸只作为换算依据交给目标（部分目标以自身实测几何为准），
        // 这里不做「尺寸未知就直接放弃」的硬判定——那会白白丢掉整次手势。
        val container = containerSize
        var best: GestureTarget? = null
        var bestPriority = 0
        diag("resolve down=$down container=$container targets=${targets.size}")
        targets.forEach { target ->
            val bounds = target.bounds(container)
            val hit = bounds != null && bounds.contains(down)
            diag("  -> ${target.javaClass.simpleName} priority=${target.priority} bounds=$bounds hit=$hit")
            if (!hit) {
                return@forEach
            }
            val priority = target.priority
            // 子节点优先：优先级不高于子节点的目标，在子节点已认领时让位
            if (consumedByChild && priority <= 0) {
                return@forEach
            }
            // 同优先级时后登记者优先（`>=` 覆盖先登记者）
            if (best != null && priority < bestPriority) {
                return@forEach
            }
            best = target
            bestPriority = priority
        }
        diag(
            "  => selected=${best?.javaClass?.simpleName ?: "null"} " +
                "id=${best?.let { System.identityHashCode(it) }} childConsumed=$consumedByChild"
        )
        return best
    }
}

@Composable
fun rememberGestureDispatchState(): GestureDispatchState {
    return remember { GestureDispatchState() }
}

/**
 * 手势分发容器：把 [targets] 登记给容器，由容器按「位置 + 优先级」分发手势。
 *
 * 容器自身**不参与命中竞争**（它是祖先节点），子节点照常收到点击；只有在目标判定接管后
 * 容器才消费事件，从而在滑动期间阻止子节点 / 滚动容器继续处理。
 *
 * ```kotlin
 * GestureDispatchHost(targets = listOf(scrollerState)) {
 *     LazyVerticalStaggeredGrid(...)
 *     HomeFastScroller(scrollerState, Modifier.align(Alignment.CenterEnd))
 * }
 * ```
 */
@Composable
fun GestureDispatchHost(
    targets: List<GestureTarget>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.gestureDispatchHost(targets, enabled),
        content = content
    )
}

/**
 * 组合式修饰符版本：把「登记目标 + 挂载分发」一起挂到**已有**的根节点上。
 *
 * 适合根节点已经存在、不想再套一层 `Box` 的场景（例如
 * [`PullToRefreshBox`][androidx.compose.material3.pulltorefresh.PullToRefreshBox] 的内容根）。
 *
 * ⚠️ 目标若要按位置命中，应以**容器尺寸为基准**换算（`bounds(container)` 的入参即本节点尺寸）；
 * 不要依赖 `onGloballyPositioned { boundsInParent() }` 这类布局回报当作坐标系依据——
 * 它在某些修饰符链上会拿到零矩形（真机实测踩过）。
 */
@Composable
fun Modifier.gestureDispatchHost(
    targets: List<GestureTarget>,
    enabled: Boolean = true
): Modifier {
    val state = rememberGestureDispatchState()
    SideEffect { state.setTargets(targets) }
    return this.gestureDispatch(state, enabled)
}

/**
 * 把容器的手势分发能力挂到根节点上。
 *
 * 分发规则（自上而下逐级收紧）：
 * 1. **子节点优先**：按下时若子节点已消费该事件，则 `priority <= 0` 的目标一律让位；
 * 2. **位置**：[GestureTarget.bounds] 命中（容器局部坐标）；
 * 3. **优先级**：命中者中 `priority` 最大者胜出，同优先级后登记者优先；
 * 4. **接管**：目标 [GestureTarget.shouldCapture] 成立后才开始消费事件；
 * 5. **抢占**：接管后在 `Initial` 阶段消费事件（先于子节点），因此能从滚动容器手里夺走拖动。
 */
fun Modifier.gestureDispatch(
    state: GestureDispatchState,
    enabled: Boolean = true
): Modifier {
    if (!enabled) {
        return this
    }
    return this
        .onSizeChanged { state.containerSize = it }
        .pointerInput(state) {
            val slop = viewConfiguration.touchSlop
            // 分两条协程守在两个指针阶段上（`PointerEventPass`）：
            //
            // - **`Initial`（自外向内，先于子节点）**：接管后必须在这里消费事件，才能把拖动从
            //   「列表自身的滚动」手里抢回来。只在 `Main` 里消费是不够的——`Main` 自内向外，
            //   滚动容器先吃过这一帧，观感就是「列表也跟手滚 + 拖拽条同时跳」两头跑。
            // - **`Main`（自内向外）**：识别与分发；此刻才能看到子节点是否已认领按下事件。
            var session: GestureTarget? = null
            var captured = false
            coroutineScope {
                launch {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (captured && event.changes.any { it.pressed }) {
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
                launch {
                    awaitPointerEventScope {
                        var pressedCount = 0
                        var down = Offset.Zero
                        var current = Offset.Zero

                        fun finish() {
                            if (captured) {
                                session?.onRelease()
                            }
                            session = null
                            captured = false
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val nowPressed = event.changes.count { it.pressed }
                            val anchor = event.changes.firstOrNull { it.pressed }
                                ?: event.changes.firstOrNull()
                            if (anchor != null) {
                                current = anchor.position
                            }

                            if (pressedCount == 0 && nowPressed > 0) {
                                // 新手势起点：先看子节点是否已经认领（Main pass 在本节点之前）
                                down = current
                                session = null
                                captured = false
                                val consumedByChild =
                                    event.changes.firstOrNull { it.pressed }?.isConsumed == true
                                diag(
                                    "DOWN pos=$down consumedByChild=$consumedByChild " +
                                        "size=${state.containerSize}"
                                )
                                session = state.resolve(down, consumedByChild)
                            } else if (nowPressed > pressedCount && captured) {
                                // 追加手指：退出本次分发，让位给双指手势
                                finish()
                            }

                            val active = session
                            if (active != null) {
                                if (!captured && active.shouldCapture(down, current, slop)) {
                                    captured = true
                                    diag(
                                        "CAPTURE by ${active.javaClass.simpleName} " +
                                            "down=$down current=$current slop=$slop"
                                    )
                                    active.onCapture(down, current)
                                }
                                if (captured) {
                                    active.onMove(current)
                                    event.changes.forEach { it.consume() }
                                }
                            }

                            if (nowPressed == 0) {
                                finish()
                                pressedCount = 0
                            } else {
                                pressedCount = nowPressed
                            }
                        }
                    }
                }
            }
        }
}

/**
 * 位移是否超过判定阈值（供目标实现复用）。
 *
 * 纵向与横向分开给出，便于目标表达「只认某一方向」的手势。
 */
fun isVerticalDrag(down: Offset, current: Offset, slop: Float): Boolean {
    return abs(current.y - down.y) > slop
}

/** 位移是否超过判定阈值（横向）。 */
fun isHorizontalDrag(down: Offset, current: Offset, slop: Float): Boolean {
    return abs(current.x - down.x) > slop
}
