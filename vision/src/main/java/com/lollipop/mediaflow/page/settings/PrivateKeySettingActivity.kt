package com.lollipop.mediaflow.page.settings

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.tools.PrivacyLock
import com.lollipop.mediaflow.ui.BasicComposeActivity

class PrivateKeySettingActivity : BasicComposeActivity() {

    private val iconKeyDelegate = IconKeyDelegate()

    private val skipBackPressLock = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
        }
    }

    private fun skip() {
        PrivacyLock.skipSetting()
        finish()
    }

    private fun save() {
        PrivacyLock.saveKey(this, iconKeyDelegate.currentKey)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, skipBackPressLock)
    }

    override fun onResume() {
        super.onResume()
        iconKeyDelegate.updateIconList()
    }

    @Composable
    override fun Content(innerPadding: PaddingValues) {
        val iconKeyList = remember { iconKeyDelegate.iconKeyList }
        val pwdFillState by remember { iconKeyDelegate.pwdFillState }
        ContentColumn(
            innerPadding = innerPadding,
            showBack = false
        ) {
            item {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.hint_private_key)
                )
            }

            item {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    iconKeyList.forEachIndexed { index, icon ->
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = MaterialTheme.shapes.large
                                )
                                .background(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.large
                                )
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val iconId = icon.key?.iconId ?: 0
                            if (iconId != 0) {
                                Icon(
                                    painter = painterResource(iconId),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        if (index < iconKeyList.size - 1) {
                            Spacer(
                                modifier = Modifier
                                    .size(24.dp)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Button(
                        onClick = {
                            iconKeyDelegate.onKeyClick(PrivacyLock.IconKey.VIDEO)
                        }
                    ) {
                        Icon(
                            painter = painterResource(PrivacyLock.IconKey.VIDEO.iconId),
                            contentDescription = null
                        )
                    }

                    Spacer(
                        modifier = Modifier
                            .width(16.dp)
                    )

                    Button(
                        onClick = {
                            iconKeyDelegate.onKeyClick(PrivacyLock.IconKey.PHOTO)
                        }
                    ) {
                        Icon(
                            painter = painterResource(PrivacyLock.IconKey.PHOTO.iconId),
                            contentDescription = null
                        )
                    }

                }
            }

            item {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Spacer(
                        modifier = Modifier
                            .weight(1F)
                            .height(16.dp)
                    )

                    OutlinedButton(
                        onClick = {
                            skip()
                        }
                    ) {
                        Text(
                            text = getString(R.string.button_skip)
                        )
                    }

                    Spacer(
                        modifier = Modifier
                            .weight(1F)
                            .height(16.dp)
                    )

                    OutlinedButton(
                        enabled = pwdFillState,
                        onClick = {
                            save()
                        }
                    ) {
                        Text(
                            text = getString(R.string.button_confirm)
                        )
                    }

                    Spacer(
                        modifier = Modifier
                            .weight(1F)
                            .height(16.dp)
                    )

                }
            }

        }
    }

    private class IconKeyDelegate {

        val iconKeyList = SnapshotStateList<Icon>()

        val pwdFillState = mutableStateOf(false)

        var currentKey = 0
            private set

        fun onKeyClick(iconKey: PrivacyLock.IconKey) {
            putKey(iconKey)
            updateIconList()
        }

        private fun putKey(iconKey: PrivacyLock.IconKey) {
            currentKey = (currentKey % PrivacyLock.PRIVATE_KEY_MASK) * 10 + iconKey.key
        }

        fun updateIconList() {
            var tempKey = currentKey
            val tempList = mutableListOf<Icon>()
            while (tempKey > 0) {
                tempList.add(0, Icon(PrivacyLock.findByKey(tempKey % 10)))
                tempKey /= 10
            }
            if (tempList.size < PrivacyLock.PRIVATE_KEY_LENGTH) {
                // 不足 4 位，补 0
                repeat(PrivacyLock.PRIVATE_KEY_LENGTH - tempList.size) {
                    tempList.add(Icon(null))
                }
            }
            pwdFillState.value = currentKey >= PrivacyLock.PRIVATE_KEY_MASK
            iconKeyList.clear()
            iconKeyList.addAll(tempList)
        }

    }

    class Icon(
        val key: PrivacyLock.IconKey?
    )

}