package com.lollipop.mediaflow.ui.guide

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lollipop.mediaflow.ui.theme.currentThemeColor

/**
 * 引导遮罩的观感参数：**所有可调数值集中于此**。
 *
 * 之所以独立成文件而不散落在组件里：遮罩的影调（变暗程度、洞留白、描边、气泡尺寸）是一套
 * 相互牵制的整体观感，分散取值必然导致后续真机调优时改漏一处、明暗主题不一致。
 *
 * 颜色只提供**取色函数**而不写成常量：本项目配色随明暗主题切换（见
 * [com.lollipop.mediaflow.ui.theme.currentThemeColor]），常量无法表达这一点。
 */
object GuideOverlayStyle {

    /** 洞相对目标矩形的默认留白：让洞比目标「松一圈」，避免贴边的局促感。 */
    val HolePadding = 8.dp

    /** 圆角矩形洞的默认圆角。 */
    val HoleCornerRadius = 12.dp

    /** 洞描边宽度：只用来勾出边界，不喧宾夺主。 */
    val HoleStrokeWidth = 1.5.dp

    /** 气泡圆角。 */
    val BubbleCornerRadius = 16.dp

    /** 气泡内边距。 */
    val BubblePadding = 16.dp

    /**
     * 气泡最大宽度。
     *
     * 限制在屏幕宽度的较窄区间内：气泡是贴着目标读的**短提示**，过宽会让视线在
     * 洞与文字之间来回扫动，反而降低可读性。
     */
    val BubbleMaxWidth = 280.dp

    /** 气泡与洞之间的最小间距。 */
    val BubbleGap = 12.dp

    /** 气泡与窗口边缘的最小间距（不含系统栏，系统栏由 insets 另行避让）。 */
    val BubbleEdgeMargin = 16.dp

    /** 标题字号。 */
    val TitleFontSize = 16.sp

    /** 正文字号。 */
    val BodyFontSize = 14.sp

    /** 脚步提示（如「点击任意位置继续」）字号。 */
    val FooterFontSize = 12.sp

    /**
     * 可见性校验容差。
     *
     * 目标与窗口求交时允许的溢出量：贴边悬浮元素（如首页右侧快速滚动条）常有一两个像素
     * 落在窗口边线之外，容差过严会把它误判为「不在屏幕上」而跳过。
     */
    val VisibleTolerance = 4.dp

    /** 深色主题下的变暗不透明度。 */
    private const val ScrimAlphaDark = 0.78F

    /** 亮色主题下的变暗不透明度。 */
    private const val ScrimAlphaLight = 0.72F

    /**
     * 遮罩颜色（半透明黑）。
     *
     * 深色主题取值略高：深色背景本身已暗，遮罩需要更强的对比如能压住外围，
     * 否则「洞内清晰、洞外变暗」的聚光感建立不起来。
     */
    @Composable
    fun scrimColor(): Color {
        val alpha = if (isSystemInDarkTheme()) ScrimAlphaDark else ScrimAlphaLight
        return Color.Black.copy(alpha = alpha)
    }

    /**
     * 洞描边颜色。
     *
     * 取主题强调色：描边横跨「洞内内容」与「洞外遮罩」两种底色，用无彩色会与遮罩糊在一起，
     * 强调色才能在两种底色上都勾出边界。
     */
    @Composable
    fun holeBorderColor(): Color {
        return currentThemeColor().buttonTextPrimary
    }

    /** 气泡底色（与其它悬浮元素同源，保持全 App 一致）。 */
    @Composable
    fun bubbleContainerColor(): Color {
        return currentThemeColor().buttonBackground
    }

    /** 气泡文字颜色。 */
    @Composable
    fun bubbleContentColor(): Color {
        return currentThemeColor().buttonText
    }

    /** 洞的形状 → 实际圆角。 */
    fun holeCornerRadius(shape: GuideHoleShape, holeWidth: Dp, holeHeight: Dp): Dp {
        return when (shape) {
            GuideHoleShape.RoundedRect -> HoleCornerRadius
            GuideHoleShape.Capsule -> holeHeight / 2
            GuideHoleShape.Circle -> minOf(holeWidth, holeHeight) / 2
        }
    }
}
