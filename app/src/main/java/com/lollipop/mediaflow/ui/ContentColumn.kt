package com.lollipop.mediaflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(
                start = innerPadding.calculateLeftPadding(layoutDirection).coerceAtLeast(16.dp),
                end = innerPadding.calculateRightPadding(layoutDirection).coerceAtLeast(16.dp),
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                // 内容列表作为模糊数据来源（与页面级浮层共享同一 HazeState）
                .effectSourceFromContext()
        ) {
            item {
                Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding()))
            }
            item {
                Spacer(modifier = Modifier.height(64.dp))
            }
            content()
            item {
                Spacer(modifier = Modifier.height(42.dp))
            }
            item {
                Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding()))
            }
        }
        // 底部系统栏边缘带：只覆盖系统栏区域；全屏时 inset 为 0 → 不添加
        if (innerPadding.calculateBottomPadding() > 0.dp) {
            EdgeEffectBandFromContext(
                edge = EdgeBand.BOTTOM,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .height(innerPadding.calculateBottomPadding() + EdgeBandFadeExtra)
            )
        }

        if (showBack) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding()))
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
}
