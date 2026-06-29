package com.lollipop.mediaflow.page.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lollipop.mediaflow.data.DevLogcat
import com.lollipop.mediaflow.ui.BasicComposeActivity

class DevLogcatActivity : BasicComposeActivity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, DevLogcatActivity::class.java).apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            })
        }
    }

    @Composable
    override fun Content(innerPadding: PaddingValues) {
        val logLines = remember { DevLogcat.logLines }
        ContentColumn(
            modifier = Modifier
                .fillMaxSize(),
            innerPadding = innerPadding,
            showBack = true
        ) {
            items(logLines, key = { it.lineValue }) {
                val color = when (it.level) {
                    DevLogcat.Level.INFO -> MaterialTheme.colorScheme.onSurface
                    DevLogcat.Level.WARN -> Color.Yellow
                    DevLogcat.Level.ERROR -> Color.Red
                }
                Text(
                    text = it.lineValue,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    fontSize = 11.sp,
                    color = color
                )
            }
        }
    }

}