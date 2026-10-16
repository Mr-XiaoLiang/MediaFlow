package com.lollipop.mediaflow.ui.guide

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * 引导展示记录（**仅存储原语**）。
 *
 * ## 职责边界
 *
 * 本对象只回答「这个引导是否已按当前版本展示过」，**不决定该不该展示**——
 * 是否展示属于页面自己的决策（见 `page` 下的各 `XxxGuide`）。
 * 因此这里不引用任何页面、不读任何业务开关，保持与业务零耦合。
 *
 * ## 记录形式：`guideKey → featureVersion`
 *
 * 键是**稳定的引导标识**，版本号是这条记录的**当前值**：
 *
 * - 键的总数恒等于引导数量，改版只是覆盖同一条记录，**不新增键、不产生垃圾数据**；
 * - 版本一致 → 不再展示；版本不一致（含从未记录）→ 展示；
 * - 改动文案或高亮目标时，把页面侧的版本常量 +1 即可重新提示一次。
 *
 * ## 为什么使用独立文件
 *
 * 与主偏好文件（`MediaFlow`）隔离，设置页的「功能帮助」重置可以**整体清空本文件**：
 * 既把所有引导记录归零，又不会误伤任何用户设置。
 *
 * ## 版本约定
 *
 * `featureVersion` 必须**从 1 起递增**；[Never] 取 `-1` 作为「从未记录」的缺省值。
 * 之所以用非法版本值而不是 `0`：否则无法区分「没有记录」与「记录为第 0 版」。
 */
object GuideSeenStore {

    private const val PREFERENCES_NAME = "MediaFlowGuide"

    /** 「从未记录」的缺省版本值：必须落在合法版本（≥ 1）之外。 */
    private const val Never = -1

    private var preferences: SharedPreferences? = null

    /**
     * 全部记录的内存缓存。
     *
     * 判定发生在页面 resume 时（见 `PageResumeSignal`），必须是一次内存读取而非磁盘读取；
     * 同时 [clearAll] 必须一并清掉它，否则用户在设置页重置后立刻返回，判定读到的仍是旧值。
     */
    private val versions = mutableMapOf<String, Int>()

    /** 由 [com.lollipop.mediaflow.LApplication] 在进程启动时注入。文件内只有若干条 int，读取成本可忽略。 */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        preferences = prefs
        versions.clear()
        prefs.all.forEach { (key, value) ->
            val version = value as? Int ?: return@forEach
            versions[key] = version
        }
    }

    /** 指定引导是否已按 [version] 展示过；从未记录返回 `false`。 */
    fun isLatest(key: String, version: Int): Boolean {
        return versions[key] == version
    }

    /** 记录指定引导已按 [version] 展示过。 */
    fun markSeen(key: String, version: Int) {
        versions[key] = version
        preferences?.edit {
            putInt(key, version)
        }
    }

    /** 清空全部记录（内存缓存 + 整个文件），用于设置页的「再次展示引导」。 */
    fun clearAll() {
        versions.clear()
        preferences?.edit {
            clear()
        }
    }
}
