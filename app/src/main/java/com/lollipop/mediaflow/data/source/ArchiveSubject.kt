package com.lollipop.mediaflow.data.source

/**
 * 归档主体：来源在 [ArchiveBackend.prepare] 产出的、**可搬运的上下文句柄**。
 *
 * ## 约定
 * - 具体类型由**来源内部定义**（Local 内部含锚定的原始项；远程内部含源路径 + 凭据），
 *   对编排与 UI **不透明**——只暴露 [tag] 与 [description]；
 * - 生命周期：`prepare` 产出 → 随归档任务排队 → `archive` 消费后即可释放；
 * - 这就是"两次操作的前后联系"载体：不再各自为政，而是同一句柄的产出与消费。
 */
interface ArchiveSubject {

    /**
     * 去重标签：**由来源内部决定判定维度**（Local 取文件地址；远程可取自己的稳定标识）。
     * 同一来源下同 [tag] 视为同一归档目标，用于在途去重。
     */
    val tag: String

    /** 描述：供任务展示（**不等于来源名称**，只是"把什么"的说明）。 */
    val description: String
}
