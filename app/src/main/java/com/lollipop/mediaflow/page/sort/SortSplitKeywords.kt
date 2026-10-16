package com.lollipop.mediaflow.page.sort

import androidx.compose.runtime.mutableStateListOf
import com.lollipop.mediaflow.tools.PreferencesBasic
import org.json.JSONArray

/**
 * 分割关键词仓库。
 *
 * 维护「已启用（有序）」与「未启用」两组关键词，并持久化到偏好设置。
 *
 * 已启用的**顺序即匹配优先级**：排序时对每个文件名按顺序检查，
 * 取**第一个命中**的关键词作为分隔词，后续关键词不再参与判定。
 * 这样不同命名规则的文件（如同时存在 `10-aaa` 与 `2_bbd`）能公平地进入同一套排序。
 *
 * 状态用 [mutableStateListOf] 暴露，界面与排序投影都会随改动自动刷新。
 */
object SortSplitKeywords {

    private const val PREF_ENABLED = "sortSplitEnabled"
    private const val PREF_DISABLED = "sortSplitDisabled"

    /** 首次使用时的默认已启用项（最常用）。 */
    private val DefaultEnabled = listOf("-")

    /** 首次使用时的默认未启用项。 */
    private val DefaultDisabled = listOf("_", "|", " ")

    private val enabledStore by lazy { PreferencesBasic.StringItem(PREF_ENABLED, "") }
    private val disabledStore by lazy { PreferencesBasic.StringItem(PREF_DISABLED, "") }

    private val enabledList = mutableStateListOf<String>()
    private val disabledList = mutableStateListOf<String>()

    private var initialized = false

    /** 已启用（有序）：顺序即匹配优先级。 */
    val enabled: List<String> get() = enabledList

    /** 未启用。 */
    val disabled: List<String> get() = disabledList

    /**
     * 在进入组合之前显式初始化，避免在快照读取期写入状态。
     */
    @Synchronized
    fun init() {
        if (initialized) {
            return
        }
        initialized = true
        val savedEnabled = decode(enabledStore.get())
        val savedDisabled = decode(disabledStore.get())
        if (savedEnabled.isEmpty() && savedDisabled.isEmpty()) {
            enabledList.addAll(DefaultEnabled)
            disabledList.addAll(DefaultDisabled)
            save()
        } else {
            enabledList.addAll(savedEnabled)
            disabledList.addAll(savedDisabled)
        }
    }

    /** 从未启用移入已启用（追加到末尾，即排在最低优先级）。 */
    fun enable(keyword: String) {
        init()
        if (!disabledList.remove(keyword)) {
            return
        }
        enabledList.add(keyword)
        save()
    }

    /** 从已启用移回未启用。 */
    fun disable(keyword: String) {
        init()
        if (!enabledList.remove(keyword)) {
            return
        }
        disabledList.add(keyword)
        save()
    }

    /** 新增关键词到未启用（空串或已存在则忽略）。 */
    fun add(keyword: String) {
        init()
        if (keyword.isEmpty()) {
            return
        }
        if (enabledList.contains(keyword) || disabledList.contains(keyword)) {
            return
        }
        disabledList.add(keyword)
        save()
    }

    private fun save() {
        enabledStore.set(encode(enabledList))
        disabledStore.set(encode(disabledList))
    }

    private fun encode(list: List<String>): String {
        val array = JSONArray()
        list.forEach { array.put(it) }
        return array.toString()
    }

    private fun decode(text: String): List<String> {
        if (text.isEmpty()) {
            return emptyList()
        }
        return try {
            val array = JSONArray(text)
            val result = ArrayList<String>(array.length())
            for (index in 0 until array.length()) {
                result.add(array.optString(index))
            }
            result
        } catch (e: Throwable) {
            emptyList()
        }
    }
}
