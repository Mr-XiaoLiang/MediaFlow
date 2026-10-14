package com.lollipop.mediaflow.ui.effect

/**
 * 悬浮元素的背景特效模式（两种模式互斥）。
 *
 * - [NONE]：纯色填充；
 * - [BLUR]：不透明底色 + 叠加模糊。
 *
 * 说明：液态玻璃（原 `GLASS`）已下线。
 *
 * [key] 用于持久化存储，**不可随意变更**。
 */
enum class VisualEffectMode(val key: Int) {

    /** 无特效：纯色填充（按主题取色）。 */
    NONE(0),

    /** 模糊：不透明底色之上叠加模糊。 */
    BLUR(1);

    companion object {

        /** 已下线的液态玻璃所用历史 key；读到它时回退为 [BLUR]。 */
        private const val LegacyGlassKey = 2

        /** 按持久化 key 查找，非法值回退为 [NONE]。 */
        fun findByKey(key: Int): VisualEffectMode {
            return entries.firstOrNull { it.key == key }
                ?: if (key == LegacyGlassKey) BLUR else NONE
        }
    }
}
