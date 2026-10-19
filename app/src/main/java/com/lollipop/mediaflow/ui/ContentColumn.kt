package com.lollipop.mediaflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.lollipop.mediaflow.ui.effect.EdgeBand
import com.lollipop.mediaflow.ui.effect.EdgeBandFadeExtra
import com.lollipop.mediaflow.ui.effect.EdgeEffectBandFromContext
import com.lollipop.mediaflow.ui.effect.FloatingButtonHeight
import com.lollipop.mediaflow.ui.effect.FloatingButtonIconPadding
import com.lollipop.mediaflow.ui.effect.FloatingButtonShape
import com.lollipop.mediaflow.ui.effect.FloatingButtonWidth
import com.lollipop.mediaflow.ui.effect.FloatingSurfaceElevation
import com.lollipop.mediaflow.ui.effect.effectSourceFromContext
import com.lollipop.mediaflow.ui.effect.effectSurfaceFromContext
import com.lollipop.mediaflow.ui.theme.currentThemeColor

@Composable
fun ContentColumn(
    modifier: Modifier = Modifier.fillMaxSize(),
    innerPadding: PaddingValues,
    showBack: Boolean = true,
    onBack: () -> Unit = {},
    content: LazyListScope.() -> Unit
) {
    val layoutDirection = LocalLayoutDirection.current
    val themeColor = currentThemeColor()
    // inset 四边的单一来源：内容避让与边缘带共用，保证两者口径一致
    val startInset = innerPadding.calculateStartPadding(layoutDirection)
    val endInset = innerPadding.calculateEndPadding(layoutDirection)
    val topInset = innerPadding.calculateTopPadding()
    val bottomInset = innerPadding.calculateBottomPadding()

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 内容区：左右各至少 16dp 内边距（系统栏 / 挖孔另有 inset），与边缘带分层，互不干扰
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = startInset.coerceAtLeast(16.dp),
                    end = endInset.coerceAtLeast(16.dp),
                )
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    // 内容列表作为模糊数据来源（与页面级浮层共享同一 HazeState）
                    .effectSourceFromContext()
            ) {
                item {
                    Spacer(modifier = Modifier.height(topInset))
                }
                item {
                    Spacer(modifier = Modifier.height(64.dp))
                }
                content()
                item {
                    Spacer(modifier = Modifier.height(42.dp))
                }
                item {
                    Spacer(modifier = Modifier.height(bottomInset))
                }
            }

            if (showBack) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Spacer(modifier = Modifier.height(topInset))
                    // 不用 Card 包裹：Card 自带一层裁剪，会把统一特效修饰符的投影裁掉。
                    // 尺寸 / 形状 / 投影 / 描边全部走共享常量，与播放页返回键完全一致（42×36dp 胶囊）。
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier
                            .size(width = FloatingButtonWidth, height = FloatingButtonHeight)
                            .effectSurfaceFromContext(
                                shape = FloatingButtonShape,
                                fallbackColor = themeColor.buttonBackground,
                                shadowElevation = FloatingSurfaceElevation,
                            )
                            .clickable {
                                onBack()
                            }
                            .padding(FloatingButtonIconPadding),
                        tint = themeColor.buttonText,
                    )
                }
            }
        }

        // 系统栏边缘带：四面齐备——上下覆盖状态栏 / 导航栏，左右覆盖横屏下位于侧边的
        // 导航栏 / 挖孔（避免导航按钮在侧边时缺少模糊兼容）。粘在屏幕最外沿，不受内容内缩影响。
        // 是否渲染由页面级系统栏模糊开关与 APP 特效总开关共同决定（见 EdgeEffectBandFromContext）。
        if (topInset > 0.dp) {
            EdgeEffectBandFromContext(
                edge = EdgeBand.TOP,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .height(topInset + EdgeBandFadeExtra)
            )
        }
        if (bottomInset > 0.dp) {
            EdgeEffectBandFromContext(
                edge = EdgeBand.BOTTOM,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .height(bottomInset + EdgeBandFadeExtra)
            )
        }
        if (startInset > 0.dp) {
            EdgeEffectBandFromContext(
                edge = EdgeBand.START,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(startInset + EdgeBandFadeExtra)
            )
        }
        if (endInset > 0.dp) {
            EdgeEffectBandFromContext(
                edge = EdgeBand.END,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(endInset + EdgeBandFadeExtra)
            )
        }
    }
}
