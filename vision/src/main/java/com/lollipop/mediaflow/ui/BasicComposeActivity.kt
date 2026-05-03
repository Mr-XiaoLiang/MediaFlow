package com.lollipop.mediaflow.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MovableContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.data.MediaVisibility
import com.lollipop.mediaflow.ui.theme.MediaFlowTheme
import com.lollipop.mediaflow.ui.theme.currentThemeColor

abstract class BasicComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediaFlowTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Content(innerPadding)
                }
            }
        }
    }

    @Composable
    abstract fun Content(innerPadding: PaddingValues)

    @Composable
    protected fun ContentColumn(
        modifier: Modifier = Modifier.fillMaxSize(),
        innerPadding: PaddingValues,
        showBack: Boolean = true,
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
                                    onBackPressedDispatcher.onBackPressed()
                                }
                                .padding(6.dp),
                            tint = currentThemeColor().buttonText,
                        )
                    }
                }
            }
        }
    }

}