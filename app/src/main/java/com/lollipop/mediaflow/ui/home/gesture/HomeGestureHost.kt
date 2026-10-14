package com.lollipop.mediaflow.ui.home.gesture

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
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 首页浮层手势的分发容器（**首页专用**）。
 *
 * ## 与视频页的关系
 *
 * 视频详情页的手势由 [`Modifier.flowGesture`][com.lollipop.mediaflow.page.flow.compose.gesture.flowGesture]
 * 独立实现，与本文件**没有任何耦合**：两者只是采用了相同的「祖先容器分发」思路，各自维护。
 * 因此首页在下面增删能力（方向仲裁 / 长按激活 / 命中区绑定）都不会影响视频页，反之亦然。
 *
 * ## 为什么需要它（而不是让浮层自己挂 `pointerInput`）
 *
 * Compose 的命中测试只把事件交给**最上层带指针输入的兄弟节点**，
 * `PointerInputModifierNode.sharePointerInputWithSiblings()` 默认 `false` 即「不允许其下方的兄弟
 * 响应事件」。因此一个叠在内容之上的浮层（例如右侧快速定位拖拽条）只要挂 `pointerInput`，
 * **下层兄弟就永远收不到事件**——哪怕该浮层「从不消费事件」也没用（事件根本没进下层兄弟的
 * 命中路径，标题行右侧的排序 / 文件夹按钮会因此点不到）。
 *
 * 正确做法是把手势识别搬到**根容器**：祖先节点本来就在子节点的命中路径上，于是
 * ① 子节点照常处理点击；② 容器只在「判定接管」后才 `consume()`，从而在拖动期间把事件抢过来。
 */
@Stable
enum class HomeGestureVerdict {

    /** 尚未达到判定条件，继续观察（既不接管，也不放弃）。 */
    Pending,

    /** 判定成立，容器开始接管本次手势。 */
    Capture,

    /**
     * 判定为「本次手势不属于本目标」：容器立即结束该目标的候选资格，**本次手势内不再询问**，
     * 事件照常交给下层（例如横向翻页）。
     *
     * 之所以需要这个状态而不是「返回未捕获」：未捕获只是「本帧不接管」，容器下一帧仍会再问；
     * 若后续帧的纵向分量又超过阈值，仍会把已经归 Pager 的横向手势抢走。
     */
    Abandon,
}

/**
 * 首页手势目标：向 [HomeGestureHost] 登记「自己的命中区 + 优先级 + 判定规则」，由容器统一分发。
 */
@Stable
interface HomeGestureTarget {

    /**
     * 命中范围（**容器局部坐标**，px）。返回 `null` 表示当前不参与分发
     * （例如条目数不足以显示拖拽条时）。
     */
    fun bounds(container: IntSize): Rect?

    /**
     * 优先级：数值越大越先拿到手势。
     *
     * 子节点（按钮 / 卡片）视作优先级 `0`：
     * - `priority <= 0` 的目标，在「子节点已消费按下事件」时一律让位；
     * - `priority > 0` 的目标仍可竞争 —— 例如侧边拖拽条要能从卡片上起手拖动。
     *
     * 同优先级时，**后登记者**优先。
     */
    val priority: Int get() = 0

    /**
     * 本次手势的判定：每帧被调用，返回 [HomeGestureVerdict]。
     *
     * @param down 手势起点（容器坐标）。
     * @param current 当前触点（容器坐标）。
     * @param slop 系统的 `touchSlop`。
     */
    fun evaluate(down: Offset, current: Offset, slop: Float): HomeGestureVerdict

    /**
     * 长按超时、且期间**未产生位移**（未触发滑动阈值、也未接管）时回调。
     *
     * 目标可据此给出「已进入模式」的反馈（例如震动），并在内部切换为激活态：
     * 之后即使斜向 / 反方向移动，也能由 [evaluate] 直接判定接管。
     */
    fun onLongPressArm() {}

    /** 接管开始。 */
    fun onCapture(down: Offset, current: Offset) {}

    /** 接管期间的每帧移动。 */
    fun onMove(current: Offset) {}

    /**
     * 本次手势结束（抬手 / 取消）。
     *
     * **无论是否已接管都会调用**：目标可在此统一复位临时状态（例如清除「长按激活」标记），
     * 避免上一次手势的残留状态影响下一次。
     */
    fun onRelease() {}
}

/**
 * 手势分发状态：持有已登记的目标，并按「位置 + 优先级」选出候选者。
 *
 * 由 [HomeGestureHost] 创建（[rememberHomeGestureState]），或在需要显式持有目标时自行创建。
 */
@Stable
class HomeGestureState internal constructor() {

    private val targets = mutableListOf<HomeGestureTarget>()

    /** 容器自身尺寸（px），由 [Modifier.homeGestureDispatch] 维护；供 [HomeGestureTarget.bounds] 换算。 */
    internal var containerSize: IntSize = IntSize.Zero

    /** 同步登记表（[HomeGestureHost] 在每次组合后调用）。 */
    internal fun setTargets(list: List<HomeGestureTarget>) {
        targets.clear()
        targets.addAll(list)
    }

    /**
     * 按「位置 + 优先级」选出候选者。
     *
     * @param consumedByChild 子节点已在 Main pass 消费了按下事件（按钮 / 卡片 / 滑块已认领）。
     * @return 候选目标；`null` 表示本次手势全部交给子节点，容器不介入。
     */
    internal fun resolve(
        down: Offset,
        consumedByChild: Boolean
    ): HomeGestureTarget? {
        // 容器尺寸只作为换算依据交给目标（部分目标以自身实测几何为准），
        // 这里不做「尺寸未知就直接放弃」的硬判定——那会白白丢掉整次手势。
        val container = containerSize
        var best: HomeGestureTarget? = null
        var bestPriority = 0
        targets.forEach { target ->
            val bounds = target.bounds(container)
            if (bounds == null || !bounds.contains(down)) {
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
        return best
    }
}

@Composable
fun rememberHomeGestureState(): HomeGestureState {
    return remember { HomeGestureState() }
}

/**
 * 首页手势分发容器：把 [targets] 登记给容器，由容器按「位置 + 优先级 + 方向判定」分发手势。
 *
 * 容器自身**不参与命中竞争**（它是祖先节点），子节点照常收到点击；只有在目标判定接管后
 * 容器才消费事件，从而在滑动期间阻止子节点 / 滚动容器继续处理。
 *
 * ```kotlin
 * HomeGestureHost(targets = listOf(scrollerState)) {
 *     LazyVerticalStaggeredGrid(...)
 *     HomeFastScroller(scrollerState, Modifier.align(Alignment.CenterEnd))
 * }
 * ```
 */
@Composable
fun HomeGestureHost(
    targets: List<HomeGestureTarget>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.homeGestureHost(targets, enabled),
        content = content
    )
}

/**
 * 组合式修饰符版本：把「登记目标 + 挂载分发」一起挂到**已有**的根节点上。
 *
 * 适合根节点已经存在、不想再套一层 `Box` 的场景。
 *
 * ⚠️ 目标若要按位置命中，应以**容器尺寸为基准**换算（`bounds(container)` 的入参即本节点尺寸）；
 * 不要依赖 `onGloballyPositioned { boundsInParent() }` 这类布局回报当作坐标系依据——
 * 它在某些修饰符链上会拿到零矩形（真机实测踩过）。
 */
@Composable
fun Modifier.homeGestureHost(
    targets: List<HomeGestureTarget>,
    enabled: Boolean = true
): Modifier {
    val state = rememberHomeGestureState()
    SideEffect { state.setTargets(targets) }
    return this.homeGestureDispatch(state, enabled)
}

/**
 * 把容器的手势分发能力挂到根节点上。
 *
 * 分发规则（自上而下逐级收紧）：
 * 1. **子节点优先**：按下时若子节点已消费该事件，则 `priority <= 0` 的目标一律让位；
 * 2. **位置**：[HomeGestureTarget.bounds] 命中（容器局部坐标）；
 * 3. **优先级**：命中者中 `priority` 最大者胜出，同优先级后登记者优先；
 * 4. **判定**：目标 [HomeGestureTarget.evaluate] 每帧给出 [HomeGestureVerdict]：
 *    - `Capture` → 开始接管；
 *    - `Abandon` → 立即结束本手势的候选资格，让位给下层（如横向翻页）；
 *    - `Pending` → 继续观察。
 * 5. **长按**：若在 `longPressTimeout` 内始终 `Pending`（无位移），回调
 *    [HomeGestureTarget.onLongPressArm]（用于「长按进入模式」的反馈与激活）。
 * 6. **抢占**：接管后在 `Initial` 阶段消费事件（先于子节点），因此能从滚动容器手里夺走拖动。
 */
fun Modifier.homeGestureDispatch(
    state: HomeGestureState,
    enabled: Boolean = true
): Modifier {
    if (!enabled) {
        return this
    }
    return this
        .onSizeChanged { state.containerSize = it }
        .pointerInput(state) {
            val slop = viewConfiguration.touchSlop
            val longPressTimeoutMillis = viewConfiguration.longPressTimeoutMillis
            // 分两条协程守在两个指针阶段上（`PointerEventPass`）：
            //
            // - **`Initial`（自外向内，先于子节点）**：接管后必须在这里消费事件，才能把拖动从
            //   「列表自身的滚动 / Pager 翻页」手里抢回来。只在 `Main` 里消费是不够的——`Main`
            //   自内向外，滚动容器先吃过这一帧，观感就是「列表也跟手滚 + 拖拽条同时跳」两头跑。
            // - **`Main`（自内向外）**：识别与分发；此刻才能看到子节点是否已认领按下事件。
            var session: HomeGestureTarget? = null
            var captured = false
            var longPressJob: Job? = null
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

                        fun cancelLongPress() {
                            longPressJob?.cancel()
                            longPressJob = null
                        }

                        fun finish() {
                            // 无条件通知：即使未接管，也让目标复位（例如清除「长按激活」态）。
                            session?.onRelease()
                            cancelLongPress()
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
                                cancelLongPress()
                                val consumedByChild =
                                    event.changes.firstOrNull { it.pressed }?.isConsumed == true
                                val target = state.resolve(down, consumedByChild)
                                session = target
                                if (target != null) {
                                    // 长按检测：超时仍未接管、且手势未改变（session 仍是它）才回调
                                    longPressJob = launch {
                                        delay(longPressTimeoutMillis)
                                        if (!captured && session === target) {
                                            target.onLongPressArm()
                                        }
                                    }
                                }
                            } else if (nowPressed > pressedCount && captured) {
                                // 追加手指：退出本次分发，让位给双指手势
                                finish()
                            }

                            val active = session
                            if (active != null && !captured) {
                                when (active.evaluate(down, current, slop)) {
                                    HomeGestureVerdict.Capture -> {
                                        captured = true
                                        cancelLongPress()
                                        active.onCapture(down, current)
                                    }
                                    HomeGestureVerdict.Abandon -> {
                                        // 放弃本目标：本次手势内不再询问，事件交还下层
                                        cancelLongPress()
                                        session = null
                                    }
                                    HomeGestureVerdict.Pending -> Unit
                                }
                            }

                            val holder = session
                            if (holder != null && captured) {
                                holder.onMove(current)
                                event.changes.forEach { it.consume() }
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
