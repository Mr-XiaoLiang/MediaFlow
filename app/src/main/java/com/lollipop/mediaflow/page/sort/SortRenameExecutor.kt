package com.lollipop.mediaflow.page.sort

import android.content.ContentResolver
import com.lollipop.mediaflow.data.local.LocalArchiveBackend
import com.lollipop.mediaflow.data.local.MediaInfo
import com.lollipop.mediaflow.tools.TaskResult

/**
 * 单条改名任务：把 [currentName] 改成 [targetName]。
 *
 * [needRename] 为 false 时无需发起 SAF 通信（名字已经符合期望），
 * 既是性能优化，也是「断点继续」时判定已完成项的依据。
 */
data class RenameTask(
    val file: MediaInfo.File,
    val currentName: String,
    val targetName: String
) {
    val needRename: Boolean get() = currentName != targetName
}

/** 因目标名被占用而顺延的记录（期望名 → 实际采用名）。 */
data class RenameConflict(
    val fileName: String,
    val expectedName: String,
    val actualName: String
)

/** 改名计划：最终任务列表（含顺延后的名字）与顺延记录。 */
data class RenamePlan(
    val tasks: List<RenameTask>,
    val conflicts: List<RenameConflict>
) {
    /** 真正需要发起改名的任务（已剔除名字未变者）。 */
    val pendingTasks: List<RenameTask> get() = tasks.filter { it.needRename }
}

/**
 * 改名计划生成器（纯计算，不发起 IO）。
 */
object SortRenamePlanner {

    /**
     * 依据目标顺序生成改名计划并处理命名冲突。
     *
     * 冲突判定基于「同一父目录内文件名唯一」这一文件系统事实：
     * - [occupiedNames] 提供各目录的既有文件名（**含未参与本次改名的文件**）；
     * - 处理每条任务前先释放它自己的旧名，避免自己与自己冲突；
     * - 目标名若仍被占用，则按 `_1`、`_2` 顺延，并记入冲突列表。
     */
    fun buildPlan(
        tasks: List<RenameTask>,
        occupiedNames: Map<String, Set<String>>
    ): RenamePlan {
        val occupied = HashMap<String, MutableSet<String>>()
        occupiedNames.forEach { (parentId, names) ->
            occupied[parentId] = names.toMutableSet()
        }
        val finalTasks = ArrayList<RenameTask>(tasks.size)
        val conflicts = ArrayList<RenameConflict>()
        for (task in tasks) {
            val parentId = task.file.parentDocId
            val names = occupied.getOrPut(parentId) { mutableSetOf() }
            names.remove(task.currentName)
            var finalName = task.targetName
            if (names.contains(finalName)) {
                val expectedName = finalName
                while (names.contains(finalName)) {
                    finalName = SortRename.nextAvailableName(finalName)
                }
                conflicts.add(
                    RenameConflict(
                        fileName = task.currentName,
                        expectedName = expectedName,
                        actualName = finalName
                    )
                )
            }
            names.add(finalName)
            finalTasks.add(RenameTask(task.file, task.currentName, finalName))
        }
        return RenamePlan(tasks = finalTasks, conflicts = conflicts)
    }
}

/** 执行结果。 */
sealed class RenameResult {

    /** 全部完成。[skipped] 为名字未变、跳过的数量。 */
    data class Completed(val renamed: Int, val skipped: Int) : RenameResult()

    /** 中途失败并中断：已完成 [renamed] 项，[task] 为失败项。 */
    data class Interrupted(
        val task: RenameTask,
        val renamed: Int,
        val error: Throwable?
    ) : RenameResult()
}

/**
 * 改名执行器：顺序执行 [RenamePlan.pendingTasks]，逐条发起 SAF 重命名。
 *
 * 失败即中断（返回 [RenameResult.Interrupted]）——已完成的部分保留，
 * 用户确认后可再次点确认，从断点继续（未变者会被自动跳过）。
 */
object SortRenameRunner {

    suspend fun run(
        resolver: ContentResolver,
        plan: RenamePlan,
        onProgress: (done: Int, total: Int) -> Unit
    ): RenameResult {
        val pending = plan.pendingTasks
        var done = 0
        for (task in pending) {
            val result = LocalArchiveBackend.rename(resolver, task.file, task.targetName)
            if (result is TaskResult.Failure) {
                return RenameResult.Interrupted(
                    task = task,
                    renamed = done,
                    error = result.error
                )
            }
            done++
            onProgress(done, pending.size)
        }
        return RenameResult.Completed(
            renamed = done,
            skipped = plan.tasks.size - pending.size
        )
    }
}
