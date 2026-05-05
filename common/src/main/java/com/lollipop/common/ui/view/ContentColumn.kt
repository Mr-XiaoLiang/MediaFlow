package com.lollipop.common.ui.view

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.lollipop.common.ui.theme.currentThemeColor


@Composable
fun ContentColumn(
    modifier: Modifier = Modifier.fillMaxSize(),
    innerPadding: PaddingValues,
    showBack: Boolean = true,
    onBack: () -> Unit = {},
    content: LazyListScope.() -> Unit
) {
    val layoutDirection = LocalLayoutDirection.current
    Box(
        modifier = modifier
            .background(currentThemeColor().windowBackground)
            .padding(
                start = innerPadding.calculateLeftPadding(layoutDirection).coerceAtLeast(16.dp),
                end = innerPadding.calculateRightPadding(layoutDirection).coerceAtLeast(16.dp),
            )
    ) {
        LazyColumn(
            modifier = Modifier.Companion
                .fillMaxSize()
        ) {
            item {
                Spacer(modifier = Modifier.Companion.height(innerPadding.calculateTopPadding()))
            }
            item {
                Spacer(modifier = Modifier.Companion.height(64.dp))
            }
            content()
            item {
                Spacer(modifier = Modifier.Companion.height(42.dp))
            }
            item {
                Spacer(modifier = Modifier.Companion.height(innerPadding.calculateBottomPadding()))
            }
        }
        if (showBack) {
            Column(
                modifier = Modifier.Companion.fillMaxSize()
            ) {
                Spacer(modifier = Modifier.Companion.height(innerPadding.calculateTopPadding()))
                Card(
                    modifier = Modifier.Companion.size(36.dp),
                    shape = CircleShape,
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = currentThemeColor().buttonBackground
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.Companion
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable {
                                onBack()
                            }
                            .padding(6.dp),
                        tint = currentThemeColor().buttonText,
                    )
                }
            }
        }
    }
}