package com.lollipop.mediaflow.ui.effect

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazePerformanceMode

/**
 * 三档语义（用户偏好项只暴露这三档）：
 *
 * - [HIGH] 最佳效果（质量优先，开销最大）；
 * - [MEDIUM] 均衡（默认）；
 * - [LOW] 降低或关闭效果。
 *
 * [key] 用于持久化存储，**不可随意变更**。
 */
enum class Level3(val key: Int) {
    LOW(0),
    MEDIUM(1),
    HIGH(2);

    companion object {
        fun findByKey(key: Int): Level3 = entries.firstOrNull { it.key == key } ?: MEDIUM
    }
}

/**
 * 画质档位（3 档）。
 *
 * [key] 用于持久化存储，**不可随意变更**。
 */
enum class PerformanceLevel(val key: Int) {
    AUTO(0),
    PERFORMANCE(1),
    QUALITY(2);

    companion object {
        fun findByKey(key: Int): PerformanceLevel = entries.firstOrNull { it.key == key } ?: AUTO
    }
}

/**
 * 档位 → 具体取值 的映射表：所有可调数值集中于此，便于后期调优。
 */
object EffectTuning {

    /** 画质档位。 */
    fun performanceMode(level: PerformanceLevel): HazePerformanceMode {
        return when (level) {
            PerformanceLevel.AUTO -> HazePerformanceMode.Default
            PerformanceLevel.PERFORMANCE -> HazePerformanceMode.Performance
            PerformanceLevel.QUALITY -> HazePerformanceMode.Quality
        }
    }

    /** 模糊半径：低 8dp / 中 20dp / 高 32dp。 */
    fun blurRadius(level: Level3): Dp {
        return when (level) {
            Level3.LOW -> 8.dp
            Level3.MEDIUM -> 20.dp
            Level3.HIGH -> 32.dp
        }
    }

    /**
     * 独立 Window 弹层（对话框 / 气泡）的**系统背景模糊半径**（dp）。
     *
     * 返回 0 表示不启用（`NONE` 模式）。
     */
    fun systemBackgroundBlurRadius(params: VisualEffectParams): Int {
        return systemBackgroundBlurRadiusForMode(params.mode)
    }

    /** [systemBackgroundBlurRadius] 的模式直查版本（供非 Compose 环境使用）。 */
    fun systemBackgroundBlurRadiusForMode(mode: VisualEffectMode): Int {
        return when (mode) {
            VisualEffectMode.NONE -> 0
            // 半径偏大一些：对话框卡片本身是半透明的，半径太小会看不出模糊效果
            VisualEffectMode.BLUR -> 48
        }
    }
}
