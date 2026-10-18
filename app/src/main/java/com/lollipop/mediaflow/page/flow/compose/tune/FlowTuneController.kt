package com.lollipop.mediaflow.page.flow.compose.tune

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.provider.Settings
import android.view.Window
import kotlin.math.roundToInt

/**
 * Flow 页「音量 / 亮度」调节的系统控制端。
 *
 * 只负责**读写系统状态**，不持有任何 Compose 状态、不参与布局：
 * - 音量：媒体流（[AudioManager.STREAM_MUSIC]），按系统音量级数取整后写入；
 * - 亮度：**窗口级**亮度（[Window.screenBrightness][android.view.WindowManager.LayoutParams.screenBrightness]），
 *   不修改系统设置，退出页面后自然恢复。
 *
 * 面板每次打开时调用 [readVolume] / [readBrightness] 取初值；手势过程中直接调用写入方法，
 * 保证「眼睛看到的值」与「实际生效的值」始终一致。
 */
class FlowTuneController(private val activity: Activity) {

    private val audioManager: AudioManager? =
        activity.getSystemService(AudioManager::class.java)

    private val window: Window = activity.window

    /** 当前媒体音量（0F..1F）。系统音量级数为 0 时返回 0F。 */
    fun readVolume(): Float {
        val manager = audioManager ?: return 0F
        val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return (current.toFloat() / max.toFloat()).coerceIn(0F, 1F)
    }

    /** 写入媒体音量：按系统级数四舍五入，`0` 表示不弹出系统音量 UI。 */
    fun writeVolume(value: Float) {
        val manager = audioManager ?: return
        val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) {
            return
        }
        val target = (value.coerceIn(0F, 1F) * max).roundToInt().coerceIn(0, max)
        manager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    }

    /**
     * 当前窗口亮度（0F..1F）。
     *
     * 窗口尚未设置过亮度时（`screenBrightness < 0`，即跟随系统），回退读取系统亮度值，
     * 这样面板初次打开时展示的就是用户「此刻看到的亮度」。
     */
    fun readBrightness(): Float {
        val windowValue = window.attributes.screenBrightness
        if (windowValue >= 0F) {
            return windowValue.coerceIn(0F, 1F)
        }
        val systemValue = runCatching {
            Settings.System.getInt(
                activity.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS
            )
        }.getOrDefault(DefaultSystemBrightness)
        return (systemValue.toFloat() / MaxSystemBrightness).coerceIn(0F, 1F)
    }

    /**
     * 写入窗口亮度。
     *
     * 下限刻意保留在 [MinWindowBrightness]：窗口亮度归零会让画面全黑，
     * 用户将无法再看见胶囊（也就无法把亮度调回来）。
     */
    fun writeBrightness(value: Float) {
        val attributes = window.attributes
        attributes.screenBrightness = value.coerceIn(MinWindowBrightness, 1F)
        window.attributes = attributes
    }

    private companion object {

        /** 系统亮度刻度的上限（[Settings.System.SCREEN_BRIGHTNESS] 常规取值 0..255）。 */
        const val MaxSystemBrightness = 255F

        /** 读取系统亮度失败时的兜底值（约一半亮度）。 */
        const val DefaultSystemBrightness = 128

        /** 窗口亮度下限：接近最暗，但仍保证面板可见、可操作。 */
        const val MinWindowBrightness = 0.01F
    }
}

/**
 * 从组合上下文中解包 [Activity]。
 *
 * 供需要窗口能力的浮层使用；非 Activity 上下文（如 Compose 预览）返回 `null`，
 * 调用方据此退化为不可用的入口，而不是抛异常。
 */
internal fun Context.findActivity(): Activity? {
    var context: Context = this
    while (context is ContextWrapper) {
        if (context is Activity) {
            return context
        }
        context = context.baseContext
    }
    return null
}
