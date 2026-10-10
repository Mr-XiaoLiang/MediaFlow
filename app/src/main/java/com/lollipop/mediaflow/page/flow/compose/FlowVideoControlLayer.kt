package com.lollipop.mediaflow.page.flow.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.data.local.ArchiveQuick
import com.lollipop.mediaflow.page.flow.compose.control.FlowGestureOsd
import com.lollipop.mediaflow.page.flow.compose.control.FlowGestureOsdStyle
import com.lollipop.mediaflow.page.flow.compose.control.FlowProgressSlider
import com.lollipop.mediaflow.page.flow.compose.control.FlowSliderColor
import com.lollipop.mediaflow.page.flow.compose.control.FlowSliderSize
import com.lollipop.mediaflow.page.flow.compose.control.FlowSliderStyle
import com.lollipop.mediaflow.page.flow.compose.control.FlowSliderTouchMode
import com.lollipop.mediaflow.ui.home.plainTextStyle

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
 * - [showPlayButton] → `playButton`（暂停或拖动进度时出现）；
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
    val showPlayButton: Boolean = false,
    val quickSpeedLabel: String = "",
    val showQuickSpeed: Boolean = false,
    val showSubtitleButton: Boolean = true,
    /** 倍速标签是否处于「非 1 倍速」启用态（决定颜色：白 / 灰）。 */
    val quickSpeedEnabled: Boolean = false,
    val archiveActions: List<ArchiveQuick> = emptyList(),
    val rewindLabel: String = "",
    val forwardLabel: String = "",
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
    sliderTouchMode: FlowSliderTouchMode = FlowSliderTouchMode.Drag
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(ControlPadding)
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

        // 左侧按钮：自下而上「字幕 → 播放 → 倍速」
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = BottomRowHeight + ControlSpacing),
            horizontalAlignment = Alignment.Start
        ) {
            if (state.showQuickSpeed) {
                FlowSpeedBadge(
                    text = state.quickSpeedLabel,
                    enabled = state.quickSpeedEnabled,
                    onClick = onQuickSpeedClick,
                    onLongClick = onQuickSpeedLongClick
                )
            }
            // 暂停按钮隐藏时保留占位：否则播放开始的瞬间它会消失，
            // 导致上方的倍速标签位置跳动
            FlowControlIcon(
                icon = R.drawable.play_circle_24px,
                visible = state.showPlayButton,
                onClick = onPlayClick
            )
            if (state.showSubtitleButton) {
                FlowControlIcon(
                    icon = R.drawable.subtitles_24,
                    onClick = onSubtitleClick
                )
            }
        }

        // 右侧归档按钮：自上而下 Favorite / Special / ThumpUp / Other
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = BottomRowHeight + ControlSpacing),
            horizontalAlignment = Alignment.End
        ) {
            state.archiveActions.forEach { quick ->
                FlowControlIcon(
                    icon = quick.iconRes,
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
                // 手势提示仅在触摸 seek 时占位：旧实现的 rewind / forward 平时是 GONE
                // （见 VideoPlayHolder.startTouchSeekMode / stopTouchSeekMode），
                // 若常驻占位会把进度条两端各挤掉 46dp，导致左右间距过大。
                if (state.rewindLabel.isNotEmpty()) {
                    FlowGestureHint(text = state.rewindLabel)
                }
                FlowProgressSlider(
                    progress = state.progress,
                    onProgressChange = onSeekProgress,
                    onTouchDown = onSeekTouchDown,
                    onTouchUp = onSeekTouchUp,
                    touchMode = sliderTouchMode,
                    style = SliderStyle,
                    modifier = Modifier
                        .weight(1F)
                        .height(BottomRowHeight)
                        .padding(horizontal = SliderHorizontalPadding)
                )
                if (state.forwardLabel.isNotEmpty()) {
                    FlowGestureHint(text = state.forwardLabel)
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

/** 图标按钮：统一外框 + 32dp 内容区内的图标。 */
@Composable
private fun FlowControlIcon(
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    visible: Boolean = true
) {
    FlowControlButton(onClick = onClick, enabled = visible) {
        Icon(
            painter = painterResource(icon),
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
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val measurer = rememberTextMeasurer()
    val textStyle = remember { plainTextStyle(SpeedBadgeTextSize, lineHeightRatio = 1F) }
    val layout = remember(text, textStyle) {
        measurer.measure(text = text, style = textStyle)
    }
    val badgeColor = if (enabled) SpeedBadgeEnabledColor else SpeedBadgeDisabledColor
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

/** 左右两侧的手势提示（`-xxS` / `+xxS`）。 */
@Composable
private fun FlowGestureHint(text: String) {
    Text(
        text = text,
        modifier = Modifier.size(width = HintSize, height = BottomRowHeight),
        style = plainTextStyle(20.sp).withShadow(),
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

/** 倍速标签文字大小（旧 XML 为 autoSize 8~20sp，取贴近其实际观感的中间值）。 */
private val SpeedBadgeTextSize = 16.sp

/** 倍速标签启用色（当前非 1 倍速，旧 `enableColor = Color.WHITE`）。 */
private val SpeedBadgeEnabledColor = Color.White

/** 倍速标签禁用色（当前 1 倍速、展示预设倍速，旧 `disableColor = Color.GRAY`）。 */
private val SpeedBadgeDisabledColor = Color.Gray

/** 滑块左右内边距（`paddingHorizontal = 16dp`）。 */
private val SliderHorizontalPadding = 16.dp

/** 快退 / 快进提示宽度（`46dp`）。 */
private val HintSize = 46.dp

/** 进度文本与滑块的间距。 */
private val ProgressTextSpacing = 4.dp

private val SliderSize = FlowSliderSize(
    active = 4.dp,
    inactive = 2.dp,
    gap = 4.dp
)

/** 已播放用主题滑块色，未播放用半透明灰（`activeColor = button_slider`、`inactiveColor = #8888`）。 */
private val SliderColor = FlowSliderColor(
    active = Color(0xBE76C7BF),
    inactive = Color(0x88888888)
)

/** 滑块样式：颜色与尺寸对齐 `page_video_flow.xml` 中 `progressSlider` 的属性。 */
private val SliderStyle = FlowSliderStyle(
    defaultSize = SliderSize,
    touchedSize = SliderSize,
    defaultColor = SliderColor,
    touchedColor = SliderColor
)
