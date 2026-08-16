package com.lollipop.mediaflow.page.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.coroutineScope
import com.lollipop.common.tools.CrashHelper
import com.lollipop.common.tools.DevLogcat
import com.lollipop.mediaflow.BuildConfig
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.ui.BasicComposeActivity
import com.lollipop.mediaflow.ui.PreferencesGroupItem
import com.lollipop.mediaflow.ui.theme.currentThemeColor
import kotlinx.coroutines.launch

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

    private val crashHelper = CrashHelper.reportDelegate(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.coroutineScope.launch {
            crashHelper.checkAndExportExitReasons()
        }
    }

    private fun clearCrashLog() {
        lifecycle.coroutineScope.launch {
            crashHelper.clearLogs()
        }
    }

    private fun shareCrash() {
        lifecycle.coroutineScope.launch {
            crashHelper.shareCrash()
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

            PreferencesGroupItem {
                CrashBlock()
            }

            if (BuildConfig.DEBUG) {
                PreferencesGroupItem {
                    Button(
                        onClick = {
                            throw RuntimeException("触发崩溃")
                        },
                    ) {
                        Text(text = "触发崩溃")
                    }
                }
            }

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

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun ColumnScope.CrashBlock() {
        val isLoading by remember { crashHelper.isLoading }
        val hasCrashLog by remember { crashHelper.hasCrashLog }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (hasCrashLog) {
                    stringResource(R.string.crash_title_has_logs)
                } else {
                    stringResource(R.string.crash_title_none_logs)
                },
                color = currentThemeColor().buttonText,
                modifier = Modifier.weight(1F),
                fontSize = 16.sp
            )
            if (isLoading) {
                ContainedLoadingIndicator(
                    modifier = Modifier.size(36.dp),
                )
            }
        }
        if (hasCrashLog) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        clearCrashLog()
                    },
                ) {
                    Text(text = stringResource(R.string.crash_button_clear_logs))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = {
                        shareCrash()
                    },
                ) {
                    Text(text = stringResource(R.string.crash_button_share_logs))
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }

}