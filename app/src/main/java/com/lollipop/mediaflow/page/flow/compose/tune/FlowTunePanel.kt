package com.lollipop.mediaflow.page.flow.compose.tune

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.ui.effect.FloatingSurfaceElevation
import com.lollipop.mediaflow.ui.effect.LocalOverlayHost
import com.lollipop.mediaflow.ui.effect.effectSurfaceFromContext
import com.lollipop.mediaflow.ui.home.plainTextStyle
import com.lollipop.mediaflow.ui.theme.currentThemeColor

/**
 * 记住「打开调节面板」的动作。
 *
 * 与气泡菜单（[com.lollipop.mediaflow.ui.home.menu.ComposePopupMenuAnchor]）走同一条浮层链路：
 * 通过 [LocalOverlayHost] 把面板提升到页面根，从而与页面内容共享同一个 HazeState，
 * 实现与菜单一致的模糊背景。
 *
 * 返回的 lambda 幂等：重复调用只是替换浮层内容。缺少浮层宿主或 Activity 上下文时
 * （例如预览环境）返回空动作，调用方无需判空。
 */
@Composable
fun rememberFlowTuneOpener(): () -> Unit {
    val overlayHost = LocalOverlayHost.current
    val context = LocalContext.current
    val controller = remember(context) {
        context.findActivity()?.let { activity -> FlowTuneController(activity) }
    }
    return remember(overlayHost, controller) {
        if (overlayHost == null || controller == null) {
            {}
        } else {
            {
                overlayHost.show {
                    FlowTunePanel(
                        controller = controller,
                        onUnmounted = { overlayHost.dismiss() },
                    )
                }
            }
        }
    }
}

/**
 * 音量 / 亮度调节面板（页面级浮层，屏幕居中）。
 *
 * 结构与气泡菜单一致：全屏透明遮罩（点击关闭）+ 居中的特效卡片。
 * 卡片内并排两个竖向胶囊，分别控制音量与亮度，胶囊底部标注文案。
 *
 * 数值的**初值在每次打开时读取**（面板离开组合树后 `remember` 即失效，下次打开重新取值），
 * 因此始终反映当下的系统音量与窗口亮度。
 */
@Composable
fun FlowTunePanel(
    controller: FlowTuneController,
    onUnmounted: () -> Unit,
) {
    // 首次组合即进入；关闭时只翻转 targetState，等退场动画播完再通知宿主卸载。
    val visibleState = remember {
        MutableTransitionState(false).apply { targetState = true }
    }
    if (!visibleState.currentState && !visibleState.targetState) {
        LaunchedEffect(Unit) { onUnmounted() }
        return
    }

    // 返回键关闭（与菜单一致）
    BackHandler { visibleState.targetState = false }

    Box(modifier = Modifier.fillMaxSize()) {
        // 遮罩：点击任意空白处关闭（卡片内的手势与按钮在其上层消费点击）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    visibleState.targetState = false
                }
        )
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            // 横屏可用高度有限：胶囊随屏幕高度收缩，保证面板整体始终落在屏内
            val capsuleHeight = minOf(TuneCapsuleHeight, maxHeight * CapsuleHeightScreenFraction)
            AnimatedVisibility(
                visibleState = visibleState,
                enter = fadeIn(animationSpec = tween(150)) +
                        scaleIn(animationSpec = tween(150), initialScale = 0.92F),
                exit = fadeOut(animationSpec = tween(100)) +
                        scaleOut(animationSpec = tween(100), targetScale = 0.92F),
            ) {
                FlowTuneCard(controller = controller, capsuleHeight = capsuleHeight)
            }
        }
    }
}

/** 面板卡片：统一特效背景（阴影 + 模糊） + 两个竖向胶囊。 */
@Composable
private fun FlowTuneCard(
    controller: FlowTuneController,
    capsuleHeight: Dp,
) {
    val themeColor = currentThemeColor()
    val shape = RoundedCornerShape(TunePanelCornerRadius)
    // 浮层每次打开都是新的组合，这里的初值即「本次打开时的最新值」
    var volume by remember { mutableFloatStateOf(controller.readVolume()) }
    var brightness by remember { mutableFloatStateOf(controller.readBrightness()) }

    Row(
        modifier = Modifier
            // ⚠️ 顺序要求：先由 padding 确定卡片尺寸，再画背景（与菜单卡片同一条链路）
            .effectSurfaceFromContext(
                shape = shape,
                fallbackColor = themeColor.buttonBackground,
                shadowElevation = FloatingSurfaceElevation,
            )
            .padding(
                horizontal = TunePanelHorizontalPadding,
                vertical = TunePanelVerticalPadding,
            ),
        horizontalArrangement = Arrangement.spacedBy(TuneCapsuleSpacing),
    ) {
        FlowTuneCapsule(
            label = stringResource(R.string.label_volume),
            value = volume,
            height = capsuleHeight,
            onValueChange = { value ->
                // 回读：系统音量按级数取整，这样填充高度始终等于真实的音量档位
                controller.writeVolume(value)
                volume = controller.readVolume()
            }
        )
        FlowTuneCapsule(
            label = stringResource(R.string.label_brightness),
            value = brightness,
            height = capsuleHeight,
            onValueChange = { value ->
                // 回读：写入值会被下限夹取，先写再读保证显示与生效值一致
                controller.writeBrightness(value)
                brightness = controller.readBrightness()
            }
        )
    }
}

/**
 * 单个竖向胶囊。
 *
 * - 外观：半透明轨道 + 自底部向上填充的主题色块，形状为全圆角胶囊；
 * - 交互：**按下即定位**（点击也能直接调节），随后手指纵向移动持续跟随，抬起结束。
 *
 * 手势直接映射到「以胶囊顶为 1、底为 0」的比例，因此上下滑动与填充高度严格同步。
 */
@Composable
private fun FlowTuneCapsule(
    label: String,
    value: Float,
    height: Dp,
    onValueChange: (Float) -> Unit,
) {
    val themeColor = currentThemeColor()
    val capsuleShape = RoundedCornerShape(percent = 50)
    // 手势协程写在 pointerInput(Unit) 中并始终读取最新回调：
    // 若直接把 lambda 作为 key，拖动过程中的每一次值变化都会重启手势识别，表现为「拖不动」。
    val latestOnValueChange = rememberUpdatedState(onValueChange)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(TuneCapsuleWidth)
                .height(height)
                .clip(capsuleShape)
                .background(themeColor.buttonText.copy(alpha = TuneCapsuleTrackAlpha))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        fun valueAt(y: Float): Float {
                            val trackHeight = size.height.toFloat()
                            if (trackHeight <= 0F) {
                                return 0F
                            }
                            return (1F - y / trackHeight).coerceIn(0F, 1F)
                        }

                        val down = awaitFirstDown()
                        down.consume()
                        latestOnValueChange.value(valueAt(down.position.y))
                        var change = down
                        while (change.pressed) {
                            val event = awaitPointerEvent()
                            change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.pressed) {
                                change.consume()
                                latestOnValueChange.value(valueAt(change.position.y))
                            }
                        }
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(value.coerceIn(0F, 1F))
                    .background(color = themeColor.buttonTextPrimary, shape = capsuleShape)
            )
        }
        Spacer(modifier = Modifier.height(TuneLabelSpacing))
        Text(
            text = label,
            style = plainTextStyle(12.sp),
            color = themeColor.buttonText,
            maxLines = 1,
        )
    }
}

/** 面板卡片圆角（与气泡菜单保持一致）。 */
private val TunePanelCornerRadius = 16.dp

/** 胶囊宽度。 */
private val TuneCapsuleWidth = 52.dp

/** 胶囊高度上限（实际取「上限」与「屏幕高度比例」的较小值）。 */
private val TuneCapsuleHeight = 200.dp

/** 胶囊高度占屏幕高度的比例：横屏时据此收缩，避免面板超出屏幕。 */
private const val CapsuleHeightScreenFraction = 0.45F

/** 两个胶囊之间的间距。 */
private val TuneCapsuleSpacing = 28.dp

/** 卡片内容的内边距。 */
private val TunePanelHorizontalPadding = 28.dp
private val TunePanelVerticalPadding = 20.dp

/** 胶囊与底部文案的间距。 */
private val TuneLabelSpacing = 10.dp

/** 轨道底色不透明度（取按钮文字色，明暗主题下都能与卡片形成对比）。 */
private const val TuneCapsuleTrackAlpha = 0.18F
