package com.lollipop.mediaflow.page.sort

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.lollipop.mediaflow.data.LMedia
import com.lollipop.mediaflow.data.common.MediaSort
import com.lollipop.mediaflow.data.local.MediaInfo
import com.lollipop.mediaflow.data.local.MediaType
import com.lollipop.mediaflow.data.local.MediaVisibility
import com.lollipop.mediaflow.data.source.MediaBackends
import com.lollipop.mediaflow.data.source.MediaCatalog
import com.lollipop.mediaflow.data.source.MediaQuery
import com.lollipop.mediaflow.data.source.MediaView

/**
 * 排序助手的数据模型。
 *
 * **与首页完全解耦**：自建 [MediaView] 指向共享 [MediaCatalog]（只读），
 * 范围（scopeId）由本模型私有持有，**绝不写回** `LocalState`，
 * 因此不会污染首页的文件夹筛选与其持久化。
 *
 * 数据流：
 * 1. 范围变化 → [MediaView] 异步重投影 → [projection] 更新；
 * 2. 页面观察 [projection] 与分割关键词变化 → [rebuild] 重建 [workingList]（自动排序）；
 * 3. 用户手动调整 [workingList] 顺序；
 * 4. [buildPlan] 依据最终顺序产出改名计划。
 *
 * 未确认即退出，则 [workingList] 随页面销毁，不产生任何副作用。
 */
class SortAssistantModel(
    val visibility: MediaVisibility,
    val mediaType: MediaType
) {

    /** 共享数据单元（只读）。与首页同源，但只是读同一份原始数据。 */
    private val catalog: MediaCatalog = MediaBackends.local.catalog(visibility)

    /** 本助手私有的范围状态。 */
    private val scopeIdState = mutableStateOf("")

    /** 私有投影视图：参数来自本模型，数据来自共享 Catalog。 */
    private val view = MediaView(
        catalog = catalog,
        queryProvider = { MediaQuery(mediaType, MediaSort.NameAsc, scopeIdState.value) },
        scope = MediaBackends.viewScope
    )

    /** 范围投影结果（尚未确定顺序）。 */
    val projection: State<List<LMedia>> get() = view.items

    /** 当前范围（目录 id，空表示全部）。 */
    val scopeId: String get() = scopeIdState.value

    /** 工作列表：自动排序 + 手动调整后的最终顺序。 */
    val workingList = mutableStateListOf<MediaInfo.File>()

    /**
     * 依据投影结果与分割关键词重建工作列表，并按文件名自然顺序自动排序。
     *
     * @param keywords 有序的分割关键词（顺序即匹配优先级）
     */
    fun rebuild(items: List<LMedia>, keywords: List<String>) {
        val files = items.filterIsInstance<MediaInfo.File>()
        val sorted = files.sortedWith(
            Comparator<MediaInfo.File> { left, right ->
                FileNameOrder.compare(left.name, right.name, keywords)
            }
        )
        Snapshot.withMutableSnapshot {
            workingList.clear()
            workingList.addAll(sorted)
        }
    }

    fun setScopeId(id: String) {
        scopeIdState.value = id
    }

    /**
     * 将第 [from] 项移动到第 [to] 项的位置（拖拽排序用）。
     */
    fun moveItem(from: Int, to: Int) {
        if (from == to) {
            return
        }
        if (from < 0 || from >= workingList.size) {
            return
        }
        if (to < 0 || to >= workingList.size) {
            return
        }
        val item = workingList.removeAt(from)
        workingList.add(to, item)
    }

    /** 将第 [index] 项上移一位。 */
    fun moveUp(index: Int) {
        if (index <= 0 || index >= workingList.size) {
            return
        }
        val item = workingList.removeAt(index)
        workingList.add(index - 1, item)
    }

    /** 将第 [index] 项下移一位。 */
    fun moveDown(index: Int) {
        if (index < 0 || index >= workingList.size - 1) {
            return
        }
        val item = workingList.removeAt(index)
        workingList.add(index + 1, item)
    }

    /** 生成改名计划（含序号宽度、目标名、冲突预检）。 */
    fun buildPlan(): RenamePlan {
        val count = workingList.size
        val width = SortRename.widthOf(count)
        val tasks = workingList.mapIndexed { index, file ->
            val originalName = SortRename.stripSoftwarePrefix(file.name)
            val targetName = SortRename.generate(index + 1, width, originalName)
            RenameTask(file = file, currentName = file.name, targetName = targetName)
        }
        return SortRenamePlanner.buildPlan(tasks, collectOccupiedNames())
    }

    /**
     * 汇总各目录内的既有文件名，供冲突检测使用。
     * 数据取自共享 Catalog 的全量快照，**包含未参与本次改名的文件**。
     */
    private fun collectOccupiedNames(): Map<String, Set<String>> {
        val result = HashMap<String, MutableSet<String>>()
        catalog.snapshot.value.items.forEach { item ->
            val file = item as? MediaInfo.File ?: return@forEach
            result.getOrPut(file.parentDocId) { mutableSetOf() }.add(file.name)
        }
        return result
    }
}
