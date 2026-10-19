package com.lollipop.mediaflow.page.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.mediaflow.ui.BasicComposeActivity
import com.lollipop.mediaflow.ui.PreferencesDivider
import com.lollipop.mediaflow.ui.PreferencesGroupItem
import com.lollipop.mediaflow.ui.PreferencesSegmented
import com.lollipop.mediaflow.ui.PreferencesSummary
import com.lollipop.mediaflow.ui.effect.Level3
import com.lollipop.mediaflow.ui.effect.PerformanceLevel
import com.lollipop.mediaflow.ui.effect.VisualEffectCapability
import com.lollipop.mediaflow.ui.effect.VisualEffectMode

/**
 * 样式设置页：设置悬浮元素的背景特效（无 / 模糊）。
 *
 * 设计要点：
 * - 入口位于偏好设置的**第一个分组之前**；Android 11（API 30）及以下不展示本页；
 * - **无实时预览**：编辑的是草稿，**返回 / 关闭本页时统一写入并生效**；
 * - 仅展示当前启用模式对应的参数分组，未启用模式的分组**不进入组合树**。
 */
class StyleSettingActivity : BasicComposeActivity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, StyleSettingActivity::class.java).apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            })
        }
    }

    // ---- 草稿状态（返回 / 关闭时一次性写入） ----
    private var draftMode by mutableStateOf(VisualEffectMode.NONE)
    private var draftPerformance by mutableStateOf(PerformanceLevel.AUTO)
    private var draftBlurRadius by mutableStateOf(Level3.MEDIUM)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 系统能力兜底：不支持样式设置的系统直接关闭本页
        if (!VisualEffectCapability.isStyleSettingAvailable()) {
            finish()
            return
        }
        loadDraft()
    }

    /** 从偏好读取当前值到草稿。 */
    private fun loadDraft() {
        draftMode = VisualEffectCapability.clampMode(
            VisualEffectMode.findByKey(Preferences.visualEffectMode.get())
        )
        draftPerformance = PerformanceLevel.findByKey(Preferences.effectPerformanceMode.get())
        draftBlurRadius = Level3.findByKey(Preferences.blurRadiusLevel.get())
    }

    /** 把草稿写入偏好（返回 / 关闭本页时生效）。 */
    private fun applyDraft() {
        Preferences.visualEffectMode.set(draftMode.key)
        Preferences.effectPerformanceMode.set(draftPerformance.key)
        Preferences.blurRadiusLevel.set(draftBlurRadius.key)
    }

    override fun onStop() {
        super.onStop()
        applyDraft()
    }

    @Composable
    override fun Content(innerPadding: PaddingValues) {
        val availableModes = remember { VisualEffectCapability.availableModes() }
        ContentColumn(
            innerPadding = innerPadding,
            showBack = true
        ) {
            // 分组一：模式选择 + 随模式变化的说明
            PreferencesGroupItem {
                PreferencesSegmented(
                    name = stringResource(R.string.label_effect_mode),
                    options = availableModes,
                    selected = draftMode,
                    labelOf = { effectModeLabel(it) },
                    onSelect = { draftMode = it }
                )
                PreferencesDivider()
                PreferencesSummary(text = effectModeSummary(draftMode))
            }

            // 分组二：模糊参数（仅模糊模式下组合）
            if (draftMode == VisualEffectMode.BLUR) {
                PreferencesGroupItem {
                    PreferencesSegmented(
                        name = stringResource(R.string.label_blur_radius),
                        options = Level3.entries,
                        selected = draftBlurRadius,
                        labelOf = { levelLabel(it) },
                        onSelect = { draftBlurRadius = it }
                    )
                    PreferencesDivider()
                    PreferencesSegmented(
                        name = stringResource(R.string.label_performance_mode),
                        options = PerformanceLevel.entries,
                        selected = draftPerformance,
                        labelOf = { performanceLabel(it) },
                        onSelect = { draftPerformance = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun effectModeLabel(mode: VisualEffectMode): String {
    return stringResource(
        when (mode) {
            VisualEffectMode.NONE -> R.string.effect_mode_none
            VisualEffectMode.BLUR -> R.string.effect_mode_blur
        }
    )
}

@Composable
private fun effectModeSummary(mode: VisualEffectMode): String {
    return stringResource(
        when (mode) {
            VisualEffectMode.NONE -> R.string.summary_effect_mode_none
            VisualEffectMode.BLUR -> R.string.summary_effect_mode_blur
        }
    )
}

@Composable
private fun levelLabel(level: Level3): String {
    return stringResource(
        when (level) {
            Level3.LOW -> R.string.level_low
            Level3.MEDIUM -> R.string.level_medium
            Level3.HIGH -> R.string.level_high
        }
    )
}

@Composable
private fun performanceLabel(level: PerformanceLevel): String {
    return stringResource(
        when (level) {
            PerformanceLevel.AUTO -> R.string.performance_auto
            PerformanceLevel.PERFORMANCE -> R.string.performance_performance
            PerformanceLevel.QUALITY -> R.string.performance_quality
        }
    )
}
