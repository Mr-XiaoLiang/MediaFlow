package com.lollipop.auditory.page

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lollipop.auditory.R
import com.lollipop.auditory.tool.LocalPermissionLauncher
import com.lollipop.auditory.ui.ContentColumn
import com.lollipop.auditory.ui.PreferencesDivider
import com.lollipop.auditory.ui.PreferencesGroupLazy
import com.lollipop.auditory.ui.PreferencesInfo
import com.lollipop.auditory.ui.PreferencesIntent

@Composable
fun PermissionPage(innerPadding: PaddingValues) {
    val permission = LocalPermissionLauncher.current
    val stateList = remember { permission.stateList }
    val context = LocalContext.current
    ContentColumn(
        innerPadding = innerPadding,
        showBack = false,
    ) {

        PreferencesGroupLazy {
            stateList.forEachIndexed { index, result ->
                if (index > 0) {
                    PreferencesDivider()
                }
                PreferencesInfo(
                    icon = {
                        val icon = if (result.isGranted) {
                            Icons.Default.Check
                        } else {
                            Icons.Default.Warning
                        }
                        Icon(
                            modifier = Modifier
                                .size(28.dp)
                                .padding(2.dp),
                            imageVector = icon,
                            contentDescription = null,
                        )
                    },
                    title = stringResource(result.permission.labelRes),
                    info = stringResource(result.permission.explanationRes)
                ) {
                    result.permission.settingsPage(context)
                }
            }

        }

        PreferencesGroupLazy {
            PreferencesIntent(
                name = stringResource(R.string.label_request_permission_audio),
                summary = stringResource(R.string.summary_request_permission_audio)
            ) {
                permission.launch()
            }
        }

    }

}