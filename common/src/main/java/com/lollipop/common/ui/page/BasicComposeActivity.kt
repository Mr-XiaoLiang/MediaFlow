package com.lollipop.common.ui.page

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lollipop.common.ui.theme.MediaFlowTheme
import com.lollipop.common.ui.view.ContentColumn

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
        ContentColumn(
            modifier = modifier,
            innerPadding = innerPadding,
            showBack = showBack,
            onBack = {
                onBackPressedDispatcher.onBackPressed()
            },
            content = content
        )
    }

}