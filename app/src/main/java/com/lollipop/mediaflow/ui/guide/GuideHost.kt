package com.lollipop.mediaflow.ui.guide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 等待锚点就绪的超时时间。
 *
 * 必须有超时：目标若始终不出现（功能被关闭、条件不满足），没有超时就会**永远停在等待态**——
 * 界面上什么都没有，返回键又被引导拦着，用户会被卡在一张「看不见的遮罩」后面。
 *
 * 取值权衡：太短会把「马上就会出现」的步骤误判为跳过；太长则让用户在无任何反馈的情况下干等。
 */
private const val AnchorWaitTimeoutMillis = 600L

/** 要求锚点位置连续稳定的帧数：跨过入场动画途中的中间位置。 */
private const val StableFrames = 1

/**
 * 引导宿主（**通用编排层**）。
 *
 * ## 它做什么 / 不做什么
 *
 * 负责：等待目标锚点、校验可见性、逐步推进、接管返回键、把「是否真的展示过」回报给页面。
 * **不负责**：判断该不该展示、如何持久化——那是页面自己的决策（见各 `XxxGuide`）。
 *
 * ## 零成本直通
 *
 * [active] 为 false 时只做一件事：把 [content] 原样输出。不建 `Box`、不 `remember`、
 * 不提供锚点注册表、不启动任何协程。因此「不需要展示引导」的页面，组合树与没有接入引导时**逐节点相同**。
 *
 * ## 内容为什么必须经 movableContentOf 承载
 *
 * [active] 由 true 变回 false 时，[content] 的调用点会从一个分支切到另一个分支。若两处直接调用
 * 同一个 `content`，Compose 会视为**两个不同的节点**（group key 不同）从而重建其全部状态——
 * 表现就是引导结束的一瞬间，首页 Pager 弹回第一页、列表滚动位置归零。
 * [movableContentOf] 让同一份内容在互斥位置间**迁移**，状态得以保留。
 *
 * ## 与页面的契约
 *
 * - 只认 [active] 的**上升沿**：页面在每次 resume 都置 true 也不会重启引导；
 * - [onFinished] 的 `wasShown` 为「整支引导中至少展示过一步」。页面据此决定是否落盘：
 *   全部步骤都因目标不可见被跳过时为 false，**不要落盘**，下次 resume 重新判定；
 * - [steps] 可以就地构造（内部只在下标变化时读取），不必由页面 `remember`。
 *
 * @param active 页面给出的展示意图；须在页面 resume 判定后置位。
 * @param steps 本支引导的步骤，按展示顺序排列。
 * @param onFinished 本轮结束回调（看完 / 跳过 / 返回键）；参数为是否真的展示过。
 * @param content 页面内容。宿主会把它放在一层全屏 `Box` 里，与遮罩同层。
 */
@Composable
fun GuideHost(
    active: Boolean,
    steps: List<GuideStep>,
    onFinished: (wasShown: Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val contentHolder = remember { movableContentOf(content) }
    if (!active) {
        contentHolder()
        return
    }
    GuideSession(
        steps = steps,
        onFinished = onFinished,
    ) {
        contentHolder()
    }
}

/**
 * 一轮引导的编排实现。
 *
 * 流程：等待锚点 → 位置稳定 → 可见性校验 → 展示 → 点击推进一步 → 全部走完回调收尾。
 * 任何一步的锚点不可用都只**跳过该步**，不会中断整支引导。
 */
@Composable
private fun GuideSession(
    steps: List<GuideStep>,
    onFinished: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val registry = remember { GuideAnchorRegistry() }
    val density = LocalDensity.current
    // 协程里必须**按需读取**窗口尺寸：resume 当帧它可能还是 0，捕获快照会一直拿到旧值
    val windowInfo = LocalWindowInfo.current
    val windowSizeProvider = { windowInfo.containerSize }
    val currentSteps by rememberUpdatedState(steps)
    val currentOnFinished by rememberUpdatedState(onFinished)

    /** 当前处理到的步骤下标；等于步骤数表示本轮已走完。 */
    var index by remember { mutableIntStateOf(0) }

    /** 当前步骤是否已通过校验、处于展示状态。 */
    var isShowing by remember { mutableStateOf(false) }

    /** 整支引导中是否至少展示过一步——决定回报给页面的 `wasShown`。 */
    var wasShown by remember { mutableStateOf(false) }

    /** 已收尾：用于在回调与页面移除宿主之间立刻停止渲染，避免多闪一帧。 */
    var isFinished by remember { mutableStateOf(false) }

    // 收尾：走完所有步骤后回报一次
    LaunchedEffect(index) {
        if (index < currentSteps.size) {
            return@LaunchedEffect
        }
        if (isFinished) {
            return@LaunchedEffect
        }
        isFinished = true
        currentOnFinished(wasShown)
    }

    // 单步推进：先等到目标可展示；不可展示（不存在 / 超时 / 不在屏幕）则跳过该步
    LaunchedEffect(index) {
        val step = currentSteps.getOrNull(index) ?: return@LaunchedEffect
        val resolved = when (val target = step.target) {
            is GuideTarget.Anchor -> awaitAnchor(
                registry = registry,
                anchorKey = target.key,
                windowSizeProvider = windowSizeProvider,
                tolerance = with(density) { GuideOverlayStyle.VisibleTolerance.toPx() },
            )

            is GuideTarget.Area -> awaitArea(
                region = target.region,
                windowSizeProvider = windowSizeProvider,
            )
        }
        if (resolved == null) {
            index += 1
        } else {
            wasShown = true
            isShowing = true
        }
    }

    val advance = {
        isShowing = false
        index += 1
    }

    // 返回键视为「结束本支引导」：既不放行去关掉页面，也不让用户被遮罩困住
    BackHandler(enabled = !isFinished) {
        isShowing = false
        index = currentSteps.size
    }

    CompositionLocalProvider(LocalGuideAnchors provides registry) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()

            val step = currentSteps.getOrNull(index)
            if (isShowing && !isFinished && step != null) {
                val padding = with(density) { step.holePadding.toPx() }
                // 组合期读取锚点：位置变化（旋转、insets、侧栏过渡）时洞会自动跟随
                val live = when (val target = step.target) {
                    is GuideTarget.Anchor -> registry.rectOf(target.key)

                    // 组合期读取窗口尺寸：旋转 / 分屏 / insets 变化时区域随之更新
                    is GuideTarget.Area -> target.region.toRect(windowSizeProvider())
                }?.let { rect -> expandHole(rect, padding) }
                val lastKnown = remember(step) { LastHoleBounds() }
                if (live != null) {
                    lastKnown.value = live
                }
                // 目标中途消失时沿用最后已知位置：此时遮罩必须继续存在，
                // 否则用户既看不到内容、又无法通过点击推进一步，会卡在死路上
                val holeBounds = live ?: lastKnown.value
                if (holeBounds != null) {
                    GuideSpotlight(
                        step = step,
                        holeBounds = holeBounds,
                        onNext = advance,
                    )
                }
            }
        }
    }
}

/**
 * 洞矩形的「最后已知值」缓存。
 *
 * 这里在组合期写入一个普通对象的字段，而没有用 `mutableStateOf`：它是一份**由输入唯一决定的记忆**，
 * 不对外产生可观察副作用，也不改变本次组合的输出（紧接着就被读取），因此是安全的。
 * 换成 Compose 状态则每帧都要额外触发一次重组，对「跟随动画中的锚点」这种逐帧变化是不必要的开销。
 */
private class LastHoleBounds {
    var value: Rect? = null
}

/**
 * 等待某步的锚点可用。
 *
 * 分三个阶段，顺序不可颠倒：
 *
 * 1. **等首次上报**——resume 时页面往往还在加载数据、入场、算 insets，此时测量必然拿不到正确坐标；
 *    首次上报代表目标已完成布局；
 * 2. **等位置稳定**——入场动画途中会出现一连串中间位置，取其中一帧会得到错误坐标；
 * 3. **可见性校验**——平稳但不在屏幕上（被滚走了）的步骤直接跳过。
 *
 * 整个等待受 [AnchorWaitTimeoutMillis] 限制：超时即视为该步不可展示。
 *
 * @return 可直接用于挖洞的目标矩形；不可展示时返回 `null`。
 */
private suspend fun awaitAnchor(
    registry: GuideAnchorRegistry,
    anchorKey: String,
    windowSizeProvider: () -> IntSize,
    tolerance: Float,
): Rect? {
    return withTimeoutOrNull(AnchorWaitTimeoutMillis) {
        val reported = waitForAnchorReport(registry, anchorKey, windowSizeProvider)
            ?: return@withTimeoutOrNull null
        val stable = waitForAnchorStable(registry, anchorKey, reported)
            ?: return@withTimeoutOrNull null
        val windowSize = windowSizeProvider()
        if (windowSize.width <= 0 || windowSize.height <= 0) {
            return@withTimeoutOrNull null
        }
        if (withinWindow(stable, windowRect(windowSize), tolerance)) {
            stable
        } else {
            null
        }
    }
}

/**
 * 等待模拟区域可用。
 *
 * 与锚点不同，区域由窗口尺寸推导，**天然落在屏幕内**，因此不需要可见性校验；
 * 唯一要等的是窗口尺寸本身——resume 当帧它可能还是 0，此时按 0 圈出来的区域是错的。
 */
private suspend fun awaitArea(
    region: GuideRegion,
    windowSizeProvider: () -> IntSize,
): Rect? {
    return withTimeoutOrNull(AnchorWaitTimeoutMillis) {
        var rect: Rect? = null
        while (rect == null) {
            withFrameNanos { }
            val windowSize = windowSizeProvider()
            if (windowSize.width > 0 && windowSize.height > 0) {
                rect = region.toRect(windowSize)
            }
        }
        rect
    }
}

/** 逐帧观察，等到锚点首次上报有效矩形。 */
private suspend fun waitForAnchorReport(
    registry: GuideAnchorRegistry,
    anchorKey: String,
    windowSizeProvider: () -> IntSize,
): Rect? {
    while (true) {
        withFrameNanos { }
        if (windowSizeProvider().width <= 0) {
            continue
        }
        val rect = registry.rectOf(anchorKey)
        if (rect != null && rect.width > 0F && rect.height > 0F) {
            return rect
        }
    }
}

/** 窗口矩形：窗口坐标下从原点铺满。 */
private fun windowRect(windowSize: IntSize): Rect {
    return Rect(
        left = 0F,
        top = 0F,
        right = windowSize.width.toFloat(),
        bottom = windowSize.height.toFloat(),
    )
}

/** 继续逐帧观察，直到位置连续 [StableFrames] 帧不变；中途锚点消失即放弃该步。 */
private suspend fun waitForAnchorStable(
    registry: GuideAnchorRegistry,
    anchorKey: String,
    initial: Rect,
): Rect? {
    var last = initial
    var stableFrames = 0
    while (stableFrames < StableFrames) {
        withFrameNanos { }
        val rect = registry.rectOf(anchorKey) ?: return null
        if (rect == last) {
            stableFrames += 1
        } else {
            last = rect
            stableFrames = 0
        }
    }
    return last
}

/** 目标矩形外扩洞留白。 */
private fun expandHole(rect: Rect, padding: Float): Rect {
    return Rect(
        left = rect.left - padding,
        top = rect.top - padding,
        right = rect.right + padding,
        bottom = rect.bottom + padding,
    )
}

/**
 * 目标是否落在窗口内（允许 [tolerance] 的溢出）。
 *
 * 允许容差是必需的：贴边悬浮元素（如首页右侧快速滚动条）常有一两个像素落在窗口边线之外，
 * 判定过严会把它误判为「不在屏幕上」而跳过。
 */
private fun withinWindow(rect: Rect, window: Rect, tolerance: Float): Boolean {
    return rect.left >= window.left - tolerance &&
        rect.top >= window.top - tolerance &&
        rect.right <= window.right + tolerance &&
        rect.bottom <= window.bottom + tolerance
}
