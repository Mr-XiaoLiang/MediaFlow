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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.lollipop.mediaflow.ui.effect.EdgeBand
import com.lollipop.mediaflow.ui.effect.EdgeBandFadeExtra
import com.lollipop.mediaflow.ui.effect.EdgeEffectBandFromContext
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
                Card(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                    // 底色改由 effectSurface 提供：无特效时为纯色，其余模式为模糊 / 玻璃
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp)
                            .effectSurfaceFromContext(
                                shape = CircleShape,
                                fallbackColor = themeColor.buttonBackground,
                            )
                            .clip(CircleShape)
                            .clickable {
                                onBack()
                            }
                            .padding(6.dp),
                        tint = themeColor.buttonText,
                    )
                }
            }
        }
    }
}
