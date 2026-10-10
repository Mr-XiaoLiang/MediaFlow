package com.lollipop.mediaflow.page.flow

import android.content.pm.ActivityInfo
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.lollipop.mediaflow.R

/**
 * 屏幕旋转模式（Flow 页共用）。
 *
 * 用途：
 * - 菜单栏旋转按钮的候选列表与图标；
 * - `Preferences.lastRotateMode` 的持久化取值（用 [tag]）。
 *
 * 说明：原先是 [com.lollipop.mediaflow.ui.BasicFlowActivity] 的内部枚举，
 * 因 Compose 外壳需要与旧外壳共用同一份定义而提取为顶层类型（旧外壳引用已同步切换）。
 */
enum class ScreenRotate(
    /** `ActivityInfo.SCREEN_ORIENTATION_*`，同时作为 `setRequestedOrientation` 与持久化取值。 */
    val tag: Int,
    @DrawableRes val icon: Int,
    @StringRes val label: Int
) {

    ROTATE_LOCK(
        tag = ActivityInfo.SCREEN_ORIENTATION_FULL_USER,
        icon = R.drawable.mobile_rotate_lock_24,
        label = R.string.screen_rotate_lock_user
    ),
    ROTATE_AUTO(
        tag = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,
        icon = R.drawable.mobile_rotate_24,
        label = R.string.screen_rotate_auto
    ),
    PORTRAIT(
        tag = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
        icon = R.drawable.mobile_lock_portrait_24,
        label = R.string.screen_rotate_lock_portrait
    ),
    LANDSCAPE(
        tag = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
        icon = R.drawable.mobile_lock_landscape_24,
        label = R.string.screen_rotate_lock_landscape
    );

    companion object {

        fun findByTag(tag: Int): ScreenRotate? {
            return entries.find { it.tag == tag }
        }

        fun findByName(name: String): ScreenRotate? {
            return entries.find { it.name == name }
        }
    }
}
