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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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

    private fun percentage(float: Float): String {
        return "${(float * 100).toInt()}%"
    }

    @Composable
    override fun Content(innerPadding: PaddingValues) {
        val isQuickPlayEnable by remember { Preferences.isQuickPlayEnable.state }
        val quickPlayMode by remember { Preferences.quickPlayMode.state }
        var playbackSpeed by remember { mutableFloatStateOf(Preferences.playbackSpeed.get()) }
        var videoTouchSeekBaseWeight by remember { mutableFloatStateOf(Preferences.videoTouchSeekBaseWeight.get()) }
        var videoTouchMaxRangeRatioY by remember { mutableFloatStateOf(Preferences.videoTouchMaxRangeRatioY.get()) }
        var playbackSpeedValue by remember { mutableStateOf(percentage(playbackSpeed)) }
        var videoTouchSeekBaseWeightValue by remember {
            mutableStateOf(
                percentage(
                    videoTouchSeekBaseWeight
                )
            )
        }
        var videoTouchMaxRangeRatioYValue by remember {
            mutableStateOf(
                percentage(
                    videoTouchMaxRangeRatioY
                )
            )
        }
        ContentColumn(
            innerPadding = innerPadding,
            showBack = true
        ) {
            PreferencesGroup {
                PreferencesSwitch(
                    name = stringResource(id = R.string.label_quick_play_enable),
                    isChecked = isQuickPlayEnable
                ) {
                    Preferences.isQuickPlayEnable.set(it)
                }

                PreferencesDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        text = stringResource(id = R.string.label_quick_play_mode),
                        color = currentThemeColor().buttonText,
                        modifier = Modifier.weight(1F)
                    )

                    SingleChoiceSegmentedButtonRow {
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

            PreferencesGroup {
                PreferencesSlide(
                    name = stringResource(
                        id = R.string.label_touch_playback_speed,
                        playbackSpeedValue
                    ),
                    valueRange = 1.5F..4F,
                    value = playbackSpeed,
                    // (4.0 - 1.5) / 0.1 - 1 = 24
                    steps = 24,
                    onValueChange = {
                        playbackSpeed = it
                        playbackSpeedValue = percentage(it)
                    },
                    onValueChangeFinished = {
                        Preferences.playbackSpeed.set(playbackSpeed)
                    }
                )

                PreferencesDivider()

                PreferencesSlide(
                    name = stringResource(
                        id = R.string.label_video_touch_seek_base_weight,
                        videoTouchSeekBaseWeightValue
                    ),
                    valueRange = 0.3F..1.2F,
                    value = videoTouchSeekBaseWeight,
                    // (1.2 - 0.3) / 0.1 - 1 = 8
                    steps = 8,
                    onValueChange = {
                        videoTouchSeekBaseWeight = it
                        videoTouchSeekBaseWeightValue = percentage(it)
                    },
                    onValueChangeFinished = {
                        Preferences.videoTouchSeekBaseWeight.set(videoTouchSeekBaseWeight)
                    }
                )

                PreferencesDivider()

                PreferencesSlide(
                    name = stringResource(
                        id = R.string.label_video_touch_max_range_ratio_y,
                        videoTouchMaxRangeRatioYValue
                    ),
                    valueRange = 0.1F..1F,
                    value = videoTouchMaxRangeRatioY,
                    // (1.0 - 0.1) / 0.1 - 1 = 8
                    steps = 8,
                    onValueChange = {
                        videoTouchMaxRangeRatioY = it
                        videoTouchMaxRangeRatioYValue = percentage(it)
                    },
                    onValueChangeFinished = {
                        Preferences.videoTouchMaxRangeRatioY.set(videoTouchMaxRangeRatioY)
                    }
                )
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
                    .padding(vertical = 4.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(color = currentThemeColor().preferencesGroup),
                content = content,
                horizontalAlignment = Alignment.CenterHorizontally
            )
        }
    }

    @Composable
    private fun PreferencesDivider() {
        HorizontalDivider(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp))
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

    @Composable
    private fun ColumnScope.PreferencesSlide(
        name: String,
        valueRange: ClosedFloatingPointRange<Float>,
        steps: Int,
        value: Float,
        onValueChange: (value: Float) -> Unit,
        onValueChangeFinished: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = name,
                color = currentThemeColor().buttonText,
            )
            Slider(
                modifier = Modifier.fillMaxWidth(),
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                onValueChangeFinished = onValueChangeFinished,
                colors = SliderDefaults.colors(
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                )
            )
        }
    }

}