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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
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
        if (showBack) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding()))
                Card(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = currentThemeColor().buttonBackground
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier
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