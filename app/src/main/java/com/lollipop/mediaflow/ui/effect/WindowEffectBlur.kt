package com.lollipop.mediaflow.ui.effect

import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.graphics.drawable.toDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import kotlin.math.roundToInt

/**
 * 为**独立 Window 的弹层**启用系统背景模糊。
 *
 * 背景说明：气泡菜单与对话框各自持有独立 Window，Haze 无法跨 Window 采样页面内容，
 * 因此这里退而使用系统级模糊（Android 12+）：
 * `WindowManager.LayoutParams.FLAG_BLUR_BEHIND` + [Window.setBackgroundBlurRadius]。
 *
 * [radiusDp] ≤ 0 表示关闭（对应 `NONE` 模式），会同时清除标志位，避免残留。
 */
fun Window.applyEffectBlur(radiusDp: Int) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return
    }
    applyEffectBlurApi31(radiusDp)
}

@RequiresApi(Build.VERSION_CODES.S)
private fun Window.applyEffectBlurApi31(radiusDp: Int) {
    if (radiusDp <= 0) {
        clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        setBackgroundBlurRadius(0)
        return
    }
    addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    // 窗口背景必须透明，否则模糊会被不透明底盖住——表现为「只有透明度、没有模糊」
    setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())
    val radiusPx = (radiusDp * context.resources.displayMetrics.density).roundToInt()
    // 同时写入 attributes：部分实现只认 attributes.blurBehindRadius，
    // 仅调用 setBackgroundBlurRadius 不会生效。
    val params = attributes
    params.blurBehindRadius = radiusPx
    attributes = params
    setBackgroundBlurRadius(radiusPx)
}

/**
 * 在 **Compose 对话框**（`androidx.compose.ui.window.Dialog`）内部调用一次，即可对其 Window 启用系统背景模糊。
 *
 * 注意：Compose 的 `Popup`（气泡菜单）不实现 [DialogWindowProvider]，调用本函数不会生效——
 * 气泡菜单改由 [EffectPalette.dialogContainerColor] 的半透明底色近似。
 */
@Composable
fun ApplyDialogWindowEffectBlur(params: VisualEffectParams) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return
    }
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window ?: return
    val radiusDp = EffectTuning.systemBackgroundBlurRadius(params)
    SideEffect {
        window.applyEffectBlur(radiusDp)
    }
}
