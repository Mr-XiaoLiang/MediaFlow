package com.lollipop.mediaflow.page.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.data.MediaLayout
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.mediaflow.ui.BasicComposeActivity
import com.lollipop.mediaflow.ui.theme.currentThemeColor

class PreferencesActivity : BasicComposeActivity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, PreferencesActivity::class.java).apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            })
        }
    }

    @Composable
    override fun Content(innerPadding: PaddingValues) {
        val isQuickPlayEnable by remember { Preferences.isQuickPlayEnable.state }
        val quickPlayMode by remember { Preferences.quickPlayMode.state }
        ContentColumn(
            innerPadding = innerPadding,
            showBack = true
        ) {
            PreferencesGroup {
                PreferencesSwitch(
                    name = "快捷播放模式",
                    isChecked = isQuickPlayEnable
                ) {
                    Preferences.isQuickPlayEnable.set(it)
                }

                HorizontalDivider()

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(
                            index = 0,
                            count = 2
                        ),
                        onClick = {
                            Preferences.quickPlayMode.set(MediaLayout.Gallery)
                        },
                        selected = quickPlayMode == MediaLayout.Gallery,
                        label = {
                            Icon(
                                painter = painterResource(R.drawable.view_carousel_24),
                                contentDescription = null
                            )
                        }
                    )
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(
                            index = 1,
                            count = 2
                        ),
                        onClick = {
                            Preferences.quickPlayMode.set(MediaLayout.Flow)
                        },
                        selected = quickPlayMode == MediaLayout.Flow,
                        label = {
                            Icon(
                                painter = painterResource(R.drawable.video_template_24),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                }
            }

        }
    }

    private fun LazyListScope.PreferencesGroup(
        content: @Composable ColumnScope.() -> Unit
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(color = currentThemeColor().preferencesGroup),
                content = content,
                horizontalAlignment = Alignment.CenterHorizontally
            )
        }
    }

    @Composable
    private fun ColumnScope.PreferencesSwitch(
        name: String,
        isChecked: Boolean,
        onCheckedChange: (isCheck: Boolean) -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                color = currentThemeColor().buttonText,
                modifier = Modifier
                    .weight(1F)
            )
            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
            )
        }
    }

}