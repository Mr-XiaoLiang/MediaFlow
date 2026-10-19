package com.lollipop.mediaflow.ui.effect

import androidx.compose.ui.graphics.Color

/**
 * 独立 Window 弹层（对话框）的配色。
 *
 * 对话框的层级顺序与气泡菜单保持一致：
 *
 * ```
 * 透明窗口 → 系统窗口模糊（在窗口背后）→ 半透明纯色（卡片本体）
 * ```
 *
 * 对话框是**独立 Window**，无法使用 Haze（跨 Window 不可采样），因此其「模糊」由
 * 系统窗口模糊承担（[com.lollipop.mediaflow.ui.effect.applyEffectBlur]，窗口背景透明）；
 * 卡片本体再叠一层**半透明**同色，既保证文字可读，又保留模糊透出。
 */
object EffectPalette {

    /**
     * 卡片本体「半透明纯色」的不透明度（`NONE` 模式仍为完全不透明）。
     *
     * 取值偏低一些，保证背后的系统窗口模糊能明显透出。
     */
    private const val DialogTintAlpha = 0.6F

    /**
     * 弹层容器底色。
     *
     * 本函数只关心「是否启用模糊」这一位信息，因此直接接收 [VisualEffectMode]，
     * 不扩散整个 [VisualEffectParams] 快照（其余字段在此与本函数无关）。
     *
     * 注意 `mode` 不能省略：对话框主题的 `windowBackground` 是**透明**的，
     * `NONE` 模式下必须由卡片自身保持不透明，否则会透出下层 Activity。
     *
     * @param mode 当前特效模式：`NONE` → 不透明纯色；`BLUR` → 半透明同色（配合系统窗口模糊）。
     * @param base 与 `NONE` 模式一致的纯色（通常为 `windowBackground` / `buttonBackground`）。
     */
    fun dialogContainerColor(mode: VisualEffectMode, base: Color): Color {
        return if (mode == VisualEffectMode.NONE) base else base.copy(alpha = DialogTintAlpha)
    }
}
