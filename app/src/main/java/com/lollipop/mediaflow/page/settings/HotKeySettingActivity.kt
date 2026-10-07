package com.lollipop.mediaflow.page.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lollipop.common.tools.HotKeyHelper
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.mediaflow.ui.BasicComposeActivity
import com.lollipop.mediaflow.ui.PreferencesDivider
import com.lollipop.mediaflow.ui.PreferencesGroupItem
import com.lollipop.mediaflow.ui.theme.currentThemeColor

class HotKeySettingActivity : BasicComposeActivity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, HotKeySettingActivity::class.java).apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            })
        }
    }

    private val focusKeyUnitState = mutableStateOf<KeyUnit?>(null)
    private val currentKeyCodeState = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HotKeyHelper.registerKeyEvent(
            window,
            onKeyDown = { code, event ->
                updateKeyEvent(event)
            }
        )
    }

    private fun cancelHotKeySet() {
        focusKeyUnitState.value = null
    }

    private fun updateHotKey(keyCode: Int) {
        currentKeyCodeState.intValue = keyCode
    }

    private fun selectKeyUnit(keyUnit: KeyUnit) {
        focusKeyUnitState.value = keyUnit
        currentKeyCodeState.intValue = when (keyUnit) {
            KeyUnit.Play -> {
                Preferences.playPauseKeyCode.get()
            }

            KeyUnit.Up -> {
                Preferences.upKeyCode.get()
            }

            KeyUnit.Down -> {
                Preferences.downKeyCode.get()
            }

            KeyUnit.Left -> {
                Preferences.leftKeyCode.get()
            }

            KeyUnit.Right -> {
                Preferences.rightKeyCode.get()
            }
        }
    }

    private fun commitHotKey() {
        val keyCode = currentKeyCodeState.intValue
        when (focusKeyUnitState.value) {
            KeyUnit.Play -> {
                Preferences.playPauseKeyCode.set(keyCode)
            }

            KeyUnit.Up -> {
                Preferences.upKeyCode.set(keyCode)
            }

            KeyUnit.Down -> {
                Preferences.downKeyCode.set(keyCode)
            }

            KeyUnit.Left -> {
                Preferences.leftKeyCode.set(keyCode)
            }

            KeyUnit.Right -> {
                Preferences.rightKeyCode.set(keyCode)
            }

            null -> {
                // do nothing
            }
        }
        focusKeyUnitState.value = null
    }

    private fun updateKeyEvent(event: android.view.KeyEvent): Boolean {
        if (focusKeyUnitState.value == null) {
            return false
        }
        updateHotKey(event.keyCode)
        return true
    }

    @Composable
    override fun Content(innerPadding: PaddingValues) {

        val playKeyCode by remember { Preferences.playPauseKeyCode.state }
        val upKeyCode by remember { Preferences.upKeyCode.state }
        val downKeyCode by remember { Preferences.downKeyCode.state }
        val leftKeyCode by remember { Preferences.leftKeyCode.state }
        val rightKeyCode by remember { Preferences.rightKeyCode.state }
        val focusKeyUnit by remember { focusKeyUnitState }
        val currentKeyCode by remember { currentKeyCodeState }

        ContentColumn(
            modifier = Modifier
                .fillMaxSize(),
            innerPadding = innerPadding,
            showBack = true
        ) {

            val focusKey = focusKeyUnit
            if (focusKey != null) {
                PreferencesGroupItem(key = "HotKeyDialog") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Icon(
                            imageVector = focusKey.icon,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .background(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentKeyCode.toString(),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            TextButton(
                                onClick = { cancelHotKeySet() },
                                modifier = Modifier.weight(1F)
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                            TextButton(
                                onClick = { commitHotKey() },
                                modifier = Modifier.weight(1F)
                            ) {
                                Text(stringResource(R.string.confirm))
                            }
                        }
                    }
                }
            }

            PreferencesGroupItem {
                HotKeyItem(KeyUnit.Play, playKeyCode) {
                    selectKeyUnit(KeyUnit.Play)
                }
                PreferencesDivider()
                HotKeyItem(KeyUnit.Up, upKeyCode) {
                    selectKeyUnit(KeyUnit.Up)
                }
                PreferencesDivider()
                HotKeyItem(KeyUnit.Down, downKeyCode) {
                    selectKeyUnit(KeyUnit.Down)
                }
                PreferencesDivider()
                HotKeyItem(KeyUnit.Left, leftKeyCode) {
                    selectKeyUnit(KeyUnit.Left)
                }
                PreferencesDivider()
                HotKeyItem(KeyUnit.Right, rightKeyCode) {
                    selectKeyUnit(KeyUnit.Right)
                }
            }
        }
    }

    @Composable
    private fun HotKeyItem(
        unit: KeyUnit,
        keyCode: Int,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onClick()
                }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = unit.icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1F)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = stringResource(unit.title),
                    color = currentThemeColor().buttonText,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 16.sp
                )
                Text(
                    text = stringResource(unit.summary),
                    color = currentThemeColor().buttonText,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 12.sp
                )
            }
            Text(
                text = keyCode.toString(),
                color = currentThemeColor().buttonText,
                modifier = Modifier,
                fontSize = 18.sp,
                style = TextStyle(fontWeight = FontWeight.Bold)
            )
        }
    }

    private enum class KeyUnit(
        val icon: ImageVector,
        val title: Int,
        val summary: Int,
    ) {
        Play(
            icon = Icons.Rounded.PlayArrow,
            title = R.string.label_hot_key_play,
            summary = R.string.summary_hot_key_play
        ),
        Up(
            icon = Icons.Rounded.KeyboardDoubleArrowUp,
            title = R.string.label_hot_key_up,
            summary = R.string.summary_hot_key_up
        ),
        Down(
            icon = Icons.Rounded.KeyboardDoubleArrowDown,
            title = R.string.label_hot_key_down,
            summary = R.string.summary_hot_key_down
        ),
        Left(
            icon = Icons.Rounded.FastRewind,
            title = R.string.label_hot_key_left,
            summary = R.string.summary_hot_key_left
        ),
        Right(
            icon = Icons.Rounded.FastForward,
            title = R.string.label_hot_key_right,
            summary = R.string.summary_hot_key_right
        )
    }

}