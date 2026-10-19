package com.lollipop.mediaflow.data.source

import android.content.Context
import com.lollipop.mediaflow.data.LMedia
import com.lollipop.mediaflow.data.local.ArchiveBasket

/**
 * 归档能力（来源轴）。
 *
 * 由某条来源的实现承担，分**两阶段**：
 * 1. [prepare]：在集合**移除之前**，把条目锚定为"可搬运的上下文"——Local 是内存原始项，
 *    远程是源路径 + 凭据；由实现内部按标识持有。这一步保证**"起码能找到原来的那条数据"**；
 * 2. [archive]：用已锚定的上下文执行搬运。
 *
 * ## 约定
 * - 入参是 [LMedia]（含标识），**不体现归档方式**；具体上下文由实现内部持有，不外泄类型；
 * - 凭据 / 会话属于来源，随 [prepare] 锚定、随句柄消费，不落到 `LMedia`、不落到任务；
 * - 每次 [archive] 完成后应释放 [prepare] 锚定的上下文；
 * - [archive] 返回是否成功；失败时**不得损坏原文件**。
 */
interface ArchiveBackend {

    /** 本归档能力对应的来源标识。 */
    val sourceId: SourceId

    /**
     * 锁定：在集合移除之前，把 [item] 锚定为可搬运的上下文**句柄**。
     *
     * @return 锚定成功的 [ArchiveSubject]；取不到（已不存在）返回 null。
     */
    suspend fun prepare(item: LMedia): ArchiveSubject?

    /**
     * 执行：用 [prepare] 产出的句柄把内容归档到 [basket]；完成后句柄即可释放。
     *
     * @param onProgress 进度回调（0..1）。
     * @return 是否成功。
     */
    suspend fun archive(
        context: Context,
        subject: ArchiveSubject,
        basket: ArchiveBasket,
        onProgress: (Float) -> Unit
    ): Boolean
}
