package com.lollipop.mediaflow.page.flow.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lollipop.mediaflow.data.local.ArchiveQuick
import com.lollipop.mediaflow.page.flow.compose.control.FlowGestureOsd
import com.lollipop.mediaflow.page.flow.compose.control.FlowGestureOsdStyle
import com.lollipop.mediaflow.page.flow.compose.control.FlowProgressSlider
import com.lollipop.mediaflow.page.flow.compose.control.FlowSliderColor
import com.lollipop.mediaflow.page.flow.compose.control.FlowSliderSize
import com.lollipop.mediaflow.page.flow.compose.control.FlowSliderStyle
import com.lollipop.mediaflow.page.flow.compose.control.FlowSliderTouchMode
import com.lollipop.mediaflow.ui.archiveIcon
import com.lollipop.mediaflow.ui.home.plainTextStyle
import com.lollipop.mediaflow.ui.theme.currentThemeColor

/** OSD 显示参数（为空表示不显示 OSD）。 */
@Immutable
data class FlowOsdState(
    val totalDuration: Long,
    val progressMs: Long,
    val baseWeight: Float,
    val precision: Float
)

/**
 * 控件层的全部可显示内容（纯数据，无行为）。
 *
 * 与旧 `page_video_flow.xml` 的 `controlLayout` 一一对应：
 * - [progress] / [progressText] → `progressSlider` / `progressTextView`；
 * - [isPaused] → `playButton`（常驻的播放 / 暂停切换按钮，图标随播放状态切换）；
 * - [quickSpeedLabel] → `quickPlaybackSpeedButtonText`（倍速圆形标签）；
 * - [archiveActions] → 右侧竖排四个归档按钮，**顺序即自上而下**（Favorite / Special / ThumpUp / Other）；
 * - [rewindLabel] / [forwardLabel] → 拖动进度时左右两侧的 `-xxS` / `+xxS` 提示；
 * - [osd] → `deconstructSpeedTextView`（刻度尺）。
 */
@Immutable
data class FlowVideoControlState(
    val progress: Float = 0F,
    val progressText: String = "",
    val showProgressText: Boolean = true,
    /** 是否处于暂停态：决定左侧播放按钮显示「播放」还是「暂停」图标。 */
    val isPaused: Boolean = false,
    val quickSpeedLabel: String = "",
    val showQuickSpeed: Boolean = false,
    val showSubtitleButton: Boolean = true,
    /** 字幕当前是否开启（true = 实心图标，false = 划线图标，对齐旧 `onTracksChanged`）。 */
    val subtitleEnabled: Boolean = false,
    /** 倍速标签是否处于「非 1 倍速」启用态（决定颜色：白 / 灰）。 */
    val quickSpeedEnabled: Boolean = false,
    val archiveActions: List<ArchiveQuick> = emptyList(),
    val rewindLabel: String = "",
    val forwardLabel: String = "",
    /**
     * 快进 / 快退按钮的秒数（如 5 表示 ±5S）；**为空表示不启用**（不显示按钮，对齐旧 `isQuickForwardEnable`）。
     * 触摸 seek 期间，两侧按钮的文本会被 [rewindLabel] / [forwardLabel] 覆盖（与旧实现同一处文本变化）。
     */
    val quickSeekSeconds: Int? = null,
    val osd: FlowOsdState? = null
)

/**
 * 视频页控件层（纯 UI）。
 *
 * 布局对照 `page_video_flow.xml`：外侧 16dp 内边距；OSD 按 `vertical_bias = 0.3` 定位于居中偏上、
 * 高 [OsdHeight]；底部一行是「快退提示 — 进度滑块 — 快进提示」；滑块上方右侧是进度文本；
 * 左下自下而上是字幕 / 播放 / 倍速；右下自下而上是归档四类。
 *
 * 交互的**判定**（单击 / 双击 / 三击、触摸 seek）由页面负责，本层只如实上报用户操作。
 */
@Composable
fun FlowVideoControlLayer(
    state: FlowVideoControlState,
    modifier: Modifier = Modifier,
    /** 滑块进度变化（0..1）。 */
    onSeekProgress: (Float) -> Unit,
    /** 滑块按下 / 抬起（页面据此进入、退出进度预览）。 */
    onSeekTouchDown: () -> Unit,
    onSeekTouchUp: () -> Unit,
    onPlayClick: () -> Unit = {},
    onQuickSpeedClick: () -> Unit = {},
    onQuickSpeedLongClick: () -> Unit = {},
    onSubtitleClick: () -> Unit = {},
    onArchiveClick: (ArchiveQuick) -> Unit = {},
    /** 快退 / 快进按钮（进度条两侧的 `-xS` / `+xS`）；仅当 `state.quickSeekSeconds != null` 时显示。 */
    onQuickRewind: () -> Unit = {},
    onQuickForward: () -> Unit = {},
    sliderTouchMode: FlowSliderTouchMode = FlowSliderTouchMode.Drag
) {
    val themeColor = currentThemeColor()
    // 滑块尺寸 / 颜色：默认态与触摸态两套（对齐旧 `DeconstructSlider.AnimationDelegate`）
    val sliderStyle = remember(themeColor) {
        FlowSliderStyle(
            defaultSize = FlowSliderSizeDefault,
            touchedSize = FlowSliderSizeTouched,
            defaultColor = FlowSliderColor(themeColor.buttonSlider, themeColor.progressInactive),
            touchedColor = FlowSliderColor(themeColor.buttonSlider, themeColor.progressInactive)
        )
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(ControlPadding)
            // 控件层与系统栏解耦：在 16dp 设计边距之外再叠加系统栏 / 刘海 insets，
            // 避免底部按钮 / 进度条在非全屏（竖屏）时压到导航栏
            // （对齐旧 `controlLayout` 由 `onInsetsChanged` 施加的 insets）
            .padding(
                WindowInsets.systemBars
                    .union(WindowInsets.displayCutout)
                    .asPaddingValues()
            )
    ) {
        // OSD：居中偏上（bias 0.3）
        val osd = state.osd
        if (osd != null) {
            val osdTop = (maxHeight - OsdHeight) * OsdVerticalBias
            FlowGestureOsd(
                totalDuration = osd.totalDuration,
                progressMs = osd.progressMs,
                baseWeight = osd.baseWeight,
                precision = osd.precision,
                style = FlowGestureOsdStyle.Default.copy(
                    lineWidth = 2.dp,
                    stepCount = 22,
                    textRatio = 0.4F,
                    textSizeRatio = 0.2F,
                    defaultLineTopRatio = 0.5F,
                    defaultLineBottomRatio = 0.8F,
                    highLineTopRatio = 0.5F,
                    highLineBottomRatio = 1.0F
                ),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = osdTop)
                    .fillMaxWidth()
                    .height(OsdHeight)
            )
        }

        // 左侧按钮：自下而上「字幕 → 播放/暂停 → 倍速」
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = ButtonColumnBottomPadding),
            horizontalAlignment = Alignment.Start
        ) {
            if (state.showQuickSpeed) {
                FlowSpeedBadge(
                    text = state.quickSpeedLabel,
                    enabled = state.quickSpeedEnabled,
                    enabledColor = themeColor.buttonSlider,
                    onClick = onQuickSpeedClick,
                    onLongClick = onQuickSpeedLongClick
                )
            }
            // 播放 / 暂停按钮常驻：图标随播放状态切换（播放中显示暂停、暂停时显示播放），
            // 点击即在两者之间切换。常驻同时避免了「按钮消失导致上方倍速标签位置跳动」。
            FlowControlIcon(
                // 播放 / 暂停统一用 Material Icons 的无外框图标：
                // 与同层的字幕 / 归档图标风格一致（此前用本地 `play_circle` 的圆圈样式，与暂停的双竖线不搭）
                imageVector = if (state.isPaused) {
                    Icons.Filled.PlayArrow
                } else {
                    Icons.Filled.Pause
                },
                onClick = onPlayClick
            )
            if (state.showSubtitleButton) {
                FlowControlIcon(
                    // 开启态用实心字幕图标，关闭态用划线图标（对齐旧 `onTracksChanged` 的切换）
                    imageVector = if (state.subtitleEnabled) {
                        Icons.Filled.Subtitles
                    } else {
                        Icons.Filled.SubtitlesOff
                    },
                    onClick = onSubtitleClick
                )
            }
        }

        // 右侧归档按钮：自上而下 Favorite / Special / ThumpUp / Other
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = ButtonColumnBottomPadding),
            horizontalAlignment = Alignment.End
        ) {
            state.archiveActions.forEach { quick ->
                FlowControlIcon(
                    imageVector = quick.archiveIcon,
                    onClick = { onArchiveClick(quick) }
                )
            }
        }

        // 底部：进度文本（屏幕水平居中）+ 「快退提示 — 滑块 — 快进提示」
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            if (state.showProgressText) {
                Text(
                    text = state.progressText,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = ProgressTextSpacing),
                    style = plainTextStyle(12.sp).withShadow(),
                    color = Color.White,
                    maxLines = 1
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BottomRowHeight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 两侧「快退 / 快进」按钮：`isQuickForwardEnable` 时常驻（对齐旧 `initQuickForward`），
                // 点击 seek ±quickSeekSeconds；触摸 seek 期间文本被 +/-xxS 覆盖。
                val quickSeek = state.quickSeekSeconds
                if (quickSeek != null) {
                    FlowQuickSeekHint(
                        text = state.rewindLabel.ifEmpty { "-${quickSeek}S" },
                        onClick = onQuickRewind
                    )
                }
                FlowProgressSlider(
                    progress = state.progress,
                    onProgressChange = onSeekProgress,
                    onTouchDown = onSeekTouchDown,
                    onTouchUp = onSeekTouchUp,
                    touchMode = sliderTouchMode,
                    style = sliderStyle,
                    modifier = Modifier
                        .weight(1F)
                        .height(BottomRowHeight)
                        .padding(horizontal = SliderHorizontalPadding)
                )
                if (quickSeek != null) {
                    FlowQuickSeekHint(
                        text = state.forwardLabel.ifEmpty { "+${quickSeek}S" },
                        onClick = onQuickForward
                    )
                }
            }
        }
    }
}

/**
 * 控件按钮的**统一外框**：48dp 点击区 + 32dp 内容区。
 *
 * 对应旧 XML 的「48dp `AppCompatImageView` + `padding = 8dp`」写法：
 * 外框（尺寸 / 点击区 / 显隐占位）由本组件统一绘制，内部内容只负责在 32dp 内容区内画东西。
 * 图标与倍速标签因此共用同一尺寸基线，不会再出现「一个 30dp 一个 32dp」的错位。
 *
 * [enabled] 为 false 时不绘制、不可点击，但**保留占位**（避免同列按钮显隐时位置跳动）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FlowControlButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(BottomIconSize)
            // 裁剪成圆形：点击涟漪（ripple）与按压反馈都按圆形渲染，
            // 方形波纹铺在视频画面上很突兀
            .clip(CircleShape)
            .alpha(if (enabled) 1F else 0F)
            .then(
                if (onLongClick == null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(ControlContentSize),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

/** 图标按钮：统一外框 + 32dp 内容区内的 Material 图标。 */
@Composable
private fun FlowControlIcon(
    imageVector: ImageVector,
    onClick: () -> Unit,
    visible: Boolean = true
) {
    FlowControlButton(onClick = onClick, enabled = visible) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * 倍速圆形标签：统一外框 + 32dp 圆内的镂空文字
 * （对齐旧 `quickPlaybackSpeedButtonText` 的 `TagTextView`：32dp、`radius = 24dp`、白/灰底块、文字挖空）。
 *
 * [enabled] 为 false 表示当前是 1 倍速、标签只是提示「预设倍速」，用灰色区分。
 */
@Composable
private fun FlowSpeedBadge(
    text: String,
    enabled: Boolean,
    enabledColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val measurer = rememberTextMeasurer()
    val textStyle = remember { plainTextStyle(SpeedBadgeTextSize, lineHeightRatio = 1F) }
    val layout = remember(text, textStyle) {
        measurer.measure(text = text, style = textStyle)
    }
    // 非 1 倍速（启用态）：主题色 `button_slider`；1 倍速：白色提示底
    // （对齐旧 `PlaybackSpeed.init` 的 enableColor = button_slider、disableColor = White）
    val badgeColor = if (enabled) enabledColor else SpeedBadgeDisabledColor
    FlowControlButton(onClick = onClick, onLongClick = onLongClick) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            val diameter = size.minDimension
            drawCircle(color = badgeColor, radius = diameter / 2F)
            val topLeft = Offset(
                x = (size.width - layout.size.width) / 2F,
                y = (size.height - layout.size.height) / 2F
            )
            drawText(
                textLayoutResult = layout,
                topLeft = topLeft,
                blendMode = BlendMode.DstOut
            )
        }
    }
}

/**
 * 进度条两侧的「快退 / 快进」按钮（文本：常态 `-xS`/`+xS`，触摸 seek 时 `-xxS`/`+xxS`）。
 *
 * 这里**只显示文本、没有独立背景**，默认波纹会铺满整个 `HintSize × BottomRowHeight` 矩形，
 * 观感很差，因此显式去掉波纹指示（`indication = null`）；点击反馈改由触觉承担——页面里的
 * 跳转收口 `seekBy()` 已统一给出 `HapticFeedbackType.ContextClick`。
 */
@Composable
private fun FlowQuickSeekHint(text: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Text(
        text = text,
        modifier = Modifier
            .size(width = HintSize, height = BottomRowHeight)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        style = plainTextStyle(QuickSeekHintTextSize).withShadow(),
        color = Color.White,
        textAlign = TextAlign.Center,
        maxLines = 1
    )
}

private fun TextStyle.withShadow(): TextStyle {
    return copy(
        shadow = androidx.compose.ui.graphics.Shadow(
            color = Color.Black,
            offset = Offset(0F, 1F),
            blurRadius = 2F
        )
    )
}

/** 控件层外边距（`controlLayout` 的 16dp）。 */
private val ControlPadding = 16.dp

/** OSD 高度（`deconstructSpeedTextView` 的 120dp）。 */
private val OsdHeight = 120.dp

/** OSD 垂直位置（`layout_constraintVertical_bias = 0.3`）。 */
private const val OsdVerticalBias = 0.3F

/** 底部行高度（`progressSlider` 的 46dp）。 */
private val BottomRowHeight = 46.dp

/** 控件按钮点击区尺寸（旧 XML 的 48dp）。 */
private val BottomIconSize = 48.dp

/** 控件按钮内容区尺寸（旧 XML `padding = 8dp` → 48 − 16 = 32dp）；图标与倍速标签都画在这个区域内。 */
private val ControlContentSize = 32.dp

/** 按钮与滑块之间的间距（`layout_marginBottom = 10dp`）。 */
private val ControlSpacing = 10.dp

/** 按钮列相对原设计的抬升量：避免按钮贴到屏幕底部的手势区 / 导航栏。 */
private val ButtonColumnLift = 16.dp

/** 左右两列按钮的底部内边距：进度条行高 + 与滑块的间距 + [ButtonColumnLift]。 */
private val ButtonColumnBottomPadding = BottomRowHeight + ControlSpacing + ButtonColumnLift

/** 倍速标签文字大小（旧 XML 为 autoSize 8~20sp，取贴近其实际观感的中间值）。 */
private val SpeedBadgeTextSize = 16.sp

/** 倍速标签禁用色（当前 1 倍速、展示预设倍速；旧 `PlaybackSpeed.init` 传的 `disableColor = Color.WHITE`）。 */
private val SpeedBadgeDisabledColor = Color.White

/** 滑块左右内边距（`paddingHorizontal = 16dp`）。 */
private val SliderHorizontalPadding = 16.dp

/** 快退 / 快进提示宽度：比原 `46dp` 更宽，让左右两侧保持从容的相对间距。 */
private val HintSize = 56.dp

/** 快退 / 快进提示文字大小：比原 `20sp` 收窄，与倍速标签同号，观感更协调。 */
private val QuickSeekHintTextSize = 16.sp

/** 进度文本与滑块的间距。 */
private val ProgressTextSpacing = 4.dp

/** 滑块默认态尺寸（旧 `initSliderAnimation.defaultSize(active = 4, inactive = 2, gap = 3)`）。 */
private val FlowSliderSizeDefault = FlowSliderSize(
    active = 4.dp,
    inactive = 2.dp,
    gap = 3.dp
)

/** 滑块触摸态尺寸（旧 `initSliderAnimation.touchedSize(active = 8, inactive = 4, gap = 6)`）：按下变粗。 */
private val FlowSliderSizeTouched = FlowSliderSize(
    active = 8.dp,
    inactive = 4.dp,
    gap = 6.dp
)
