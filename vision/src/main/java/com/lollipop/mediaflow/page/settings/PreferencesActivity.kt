package com.lollipop.mediaflow.page.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.lollipop.mediaflow.BuildConfig
import com.lollipop.mediaflow.R
import com.lollipop.common.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.common.tools.safeRun
import com.lollipop.mediaflow.ui.BasicComposeActivity
import com.lollipop.mediaflow.ui.PreferencesDivider
import com.lollipop.mediaflow.ui.PreferencesGroupItem
import com.lollipop.mediaflow.ui.PreferencesIntent
import com.lollipop.mediaflow.ui.PreferencesSlide
import com.lollipop.mediaflow.ui.PreferencesSwitch
import com.lollipop.mediaflow.ui.theme.currentThemeColor
import com.lollipop.common.upgrade.GithubApiModel
import kotlinx.coroutines.launch


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

    private val appUpdateState = mutableStateOf(UpdateState.Idle)
    private val appUpdateBody = mutableStateOf("")
    private var appUpdateUrl = mutableStateOf("")

    private val log by lazy {
        registerLog()
    }

    private fun onUpdateButtonClick() {
        if (appUpdateState.value == UpdateState.HasUpdate) {
            safeRun {
                val url = appUpdateUrl.value
                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
            return
        }
        appUpdateState.value = UpdateState.Fetching
        val currentVersionCode = BuildConfig.VERSION_CODE
        lifecycleScope.launch {
            GithubApiModel.fetch().onSuccess { info ->
                if (info.versionCode > currentVersionCode) {
                    val url = info.assets.firstOrNull {
                        it.name.endsWith("apk", ignoreCase = true)
                    }?.url ?: ""
                    if (url.isNotEmpty()) {
                        appUpdateState.value = UpdateState.HasUpdate
                        appUpdateBody.value = info.tagName + "\n" + info.updateInfo
                        appUpdateUrl.value = url
                    } else {
                        appUpdateState.value = UpdateState.NoUpdate
                    }
                } else {
                    appUpdateState.value = UpdateState.NoUpdate
                }
                log.i(
                    """
                                GithubApiModel.fetch()
                                tagName = ${info.tagName}
                                versionName = ${info.versionName}
                                versionCode = ${info.versionCode}
                                assets = ${info.assets.joinToString(separator = "\n") { "${it.name} - ${it.url}" }}
                                updateInfo = ${info.updateInfo}
                            """.trimIndent()
                )
            }.onFailure {
                appUpdateState.value = UpdateState.NoUpdate
                log.e("GithubApiModel.fetch()", it)
            }
        }
    }

    private fun openGitHub() {
        safeRun {
            val url = "https://github.com/Mr-XiaoLiang/MediaFlow"
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }

    private fun openQQ() {
        safeRun {
            val url = "https://qm.qq.com/q/8ezA5OKSWc"
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }

    @Composable
    override fun Content(innerPadding: PaddingValues) {
        val activity = this
        var playbackSpeed by remember { mutableFloatStateOf(Preferences.playbackSpeed.get()) }
        var videoTouchSeekBaseWeight by remember { mutableFloatStateOf(Preferences.videoTouchSeekBaseWeight.get()) }
        var videoTouchMaxRangeRatioY by remember { mutableFloatStateOf(Preferences.videoTouchMaxRangeRatioY.get()) }
        var playbackSpeedValue by remember { mutableStateOf(percentage(playbackSpeed)) }
        val isQuickArchiveEnable by remember { Preferences.isQuickArchiveEnable.state }
        val isShowOtherQuickArchiveButton by remember { Preferences.isShowOtherQuickArchiveButton.state }
        val isBlurVideoBackground by remember { Preferences.isBlurVideoBackground.state }
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
        val updateState by remember { appUpdateState }
        val updateBody by remember { appUpdateBody }

        val isSidePanelGestureEnable by remember { Preferences.isSidePanelGestureEnable.state }
        val isShowFullscreenBtn by remember { Preferences.isShowFullscreenBtn.state }
        val isShowSidePanelBtn by remember { Preferences.isShowSidePanelBtn.state }
        val isShowDrawerBtn by remember { Preferences.isShowDrawerBtn.state }
        val isShowBackBtn by remember { Preferences.isShowBackBtn.state }
        val isShowTitle by remember { Preferences.isShowTitle.state }
        val isShowTag by remember { Preferences.isShowTag.state }

        ContentColumn(
            innerPadding = innerPadding,
            showBack = true
        ) {

            PreferencesGroupItem {
                PreferencesSlide(
                    name = stringResource(
                        id = R.string.label_touch_playback_speed,
                        playbackSpeedValue
                    ),
                    valueRange = Preferences.playbackSpeedRange,
                    value = playbackSpeed,
                    // (4.0 - 0.5) / 0.1 - 1 = 34
                    steps = getSteps(Preferences.playbackSpeedRange, 0.01F),
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
                    valueRange = Preferences.videoTouchSeekBaseWeightRange,
                    value = videoTouchSeekBaseWeight,
                    // (1.2 - 0.3) / 0.1 - 1 = 8
                    steps = getSteps(Preferences.videoTouchSeekBaseWeightRange, 0.01F),
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
                    valueRange = Preferences.videoTouchMaxRangeRatioYRange,
                    value = videoTouchMaxRangeRatioY,
                    // (1.0 - 0.1) / 0.1 - 1 = 8
                    steps = getSteps(Preferences.videoTouchMaxRangeRatioYRange, 0.01F),
                    onValueChange = {
                        videoTouchMaxRangeRatioY = it
                        videoTouchMaxRangeRatioYValue = percentage(it)
                    },
                    onValueChangeFinished = {
                        Preferences.videoTouchMaxRangeRatioY.set(videoTouchMaxRangeRatioY)
                    }
                )
            }

            PreferencesGroupItem {
                PreferencesSwitch(
                    name = stringResource(id = R.string.label_play_is_show_back_button),
                    summary = stringResource(id = R.string.summary_play_is_show_back_button),
                    isChecked = isShowBackBtn
                ) {
                    Preferences.isShowBackBtn.set(it)
                }
                PreferencesDivider()
                PreferencesSwitch(
                    name = stringResource(id = R.string.label_play_is_show_title),
                    summary = stringResource(id = R.string.summary_play_is_show_title),
                    isChecked = isShowTitle
                ) {
                    Preferences.isShowTitle.set(it)
                }
                PreferencesDivider()
                PreferencesSwitch(
                    name = stringResource(id = R.string.label_play_is_show_tag),
                    summary = stringResource(id = R.string.summary_play_is_show_tag),
                    isChecked = isShowTag
                ) {
                    Preferences.isShowTag.set(it)
                }
                PreferencesDivider()
                PreferencesSwitch(
                    name = stringResource(id = R.string.label_play_is_show_fullscreen_button),
                    summary = stringResource(id = R.string.summary_play_is_show_fullscreen_button),
                    isChecked = isShowFullscreenBtn
                ) {
                    Preferences.isShowFullscreenBtn.set(it)
                }
                PreferencesDivider()
                PreferencesSwitch(
                    name = stringResource(id = R.string.label_play_is_show_side_button),
                    summary = stringResource(id = R.string.summary_play_is_show_side_button),
                    isChecked = isShowSidePanelBtn
                ) {
                    Preferences.isShowSidePanelBtn.set(it)
                }
                PreferencesDivider()
                PreferencesSwitch(
                    name = stringResource(id = R.string.label_play_is_show_drawer_button),
                    summary = stringResource(id = R.string.summary_play_is_show_drawer_button),
                    isChecked = isShowDrawerBtn
                ) {
                    Preferences.isShowDrawerBtn.set(it)
                }
                PreferencesDivider()
                PreferencesSwitch(
                    name = stringResource(id = R.string.label_side_panel_gesture_enable),
                    summary = stringResource(id = R.string.summary_side_panel_gesture_enable),
                    isChecked = isSidePanelGestureEnable
                ) {
                    Preferences.isSidePanelGestureEnable.set(it)
                }
            }

            PreferencesGroupItem {
                PreferencesIntent(
                    name = stringResource(id = R.string.label_archive_uri),
                    summary = stringResource(id = R.string.summary_archive_uri)
                ) {
                    ArchiveUriManagerActivity.start(activity)
                }

                PreferencesDivider()

                PreferencesSwitch(
                    name = stringResource(id = R.string.label_quick_archive_enable),
                    summary = stringResource(id = R.string.summary_quick_archive_enable),
                    isChecked = isQuickArchiveEnable
                ) {
                    Preferences.isQuickArchiveEnable.set(it)
                }

                PreferencesDivider()

                PreferencesSwitch(
                    name = stringResource(id = R.string.label_show_other_quick_archive),
                    summary = stringResource(id = R.string.summary_show_other_quick_archive),
                    isChecked = isShowOtherQuickArchiveButton
                ) {
                    Preferences.isShowOtherQuickArchiveButton.set(it)
                }

            }

            PreferencesGroupItem {

                PreferencesSwitch(
                    name = stringResource(id = R.string.label_video_blur),
                    summary = stringResource(id = R.string.summary_video_blur),
                    isChecked = isBlurVideoBackground
                ) {
                    Preferences.isBlurVideoBackground.set(it)
                }

            }

            PreferencesGroupItem {
                val updateStateInfo = when (updateState) {
                    UpdateState.Idle -> {
                        stringResource(id = R.string.summary_check_update_idle)
                    }

                    UpdateState.Fetching -> {
                        stringResource(id = R.string.summary_check_update_fetching)
                    }

                    UpdateState.HasUpdate -> {
                        updateBody
                    }

                    UpdateState.NoUpdate -> {
                        stringResource(id = R.string.summary_check_update_nothing)
                    }
                }
                PreferencesIntent(
                    name = stringResource(id = R.string.label_check_update),
                    summary = updateStateInfo
                ) {
                    onUpdateButtonClick()
                }

                PreferencesDivider()

                PreferencesIntent(
                    name = stringResource(id = R.string.label_github),
                    summary = stringResource(id = R.string.summary_github),
                ) {
                    openGitHub()
                }

                PreferencesDivider()

                PreferencesIntent(
                    name = stringResource(id = R.string.label_fallback),
                    summary = stringResource(id = R.string.summary_fallback),
                ) {
                    openQQ()
                }
            }

            item {
                Text(
                    text = BuildConfig.VERSION_NAME,
                    color = currentThemeColor().buttonText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            }
        }
    }


    private fun getSteps(range: ClosedFloatingPointRange<Float>, stepLength: Float): Int {
        // (1.0 - 0.1) / 0.1 - 1 = 8
        return ((range.endInclusive - range.start) / stepLength).toInt() - 1
    }

    private enum class UpdateState {
        Idle,
        Fetching,
        HasUpdate,
        NoUpdate
    }

}