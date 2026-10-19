package com.lollipop.mediaflow.ui.effect

import android.os.Build

/**
 * 特效参数快照：在组合期读取一次后向下传递，避免效果实现侧逐帧读取偏好设置。
 * 所有可调项均以「档位」形式承载，具体取值由 [EffectTuning] 映射。
 */
data class VisualEffectParams(
    /** 当前生效的特效模式（已按系统能力收敛）。 */
    val mode: VisualEffectMode,
    val performanceLevel: PerformanceLevel,
    val blurRadiusLevel: Level3,
) {

    /** 是否处于「无特效」路径。 */
    val isNone: Boolean
        get() = mode == VisualEffectMode.NONE
}

/**
 * 系统能力判定与偏好值收敛。
 *
 * 能力矩阵：
 * - Android 12+（API 31+）：无 / 模糊；
 * - Android 11-（API ≤30）：仅无（不展示样式设置入口）。
 */
object VisualEffectCapability {

    /** 当前系统下的默认模式：Android 12+ 模糊，其余无。 */
    fun defaultMode(): VisualEffectMode {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VisualEffectMode.BLUR
        } else {
            VisualEffectMode.NONE
        }
    }

    /** 当前系统可选的模式集合（供样式设置页展示）。 */
    fun availableModes(): List<VisualEffectMode> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(VisualEffectMode.NONE, VisualEffectMode.BLUR)
        } else {
            listOf(VisualEffectMode.NONE)
        }
    }

    /** 是否展示「样式设置」入口：Android 11（API 30）及以下不展示。 */
    fun isStyleSettingAvailable(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    /**
     * 把任意来源的模式值收敛到当前系统能力范围。
     *
     * ≤ API 30 一律锁定为 [VisualEffectMode.NONE]。
     */
    fun clampMode(mode: VisualEffectMode): VisualEffectMode {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mode
        } else {
            VisualEffectMode.NONE
        }
    }
}
