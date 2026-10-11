package com.lollipop.mediaflow.tools

import android.content.Context
import com.lollipop.mediaflow.data.LMedia
import com.lollipop.mediaflow.data.local.ArchiveBasket
import com.lollipop.mediaflow.data.local.ArchiveManager
import com.lollipop.mediaflow.data.local.ArchiveQuick
import com.lollipop.mediaflow.data.local.MediaVisibility
import com.lollipop.mediaflow.data.source.ArchiveSubject
import com.lollipop.mediaflow.data.source.MediaBackends
import com.lollipop.mediaflow.data.source.SourceId
import com.lollipop.mediaflow.page.archive.ArchiveSelectDialog
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import kotlinx.coroutines.Dispatchers

/**
 * 归档 / 移除帮助类（**唯一入口**）。
 *
 * 入口负责"发起"：按来源检索归档能力 → `prepare` **锁定上下文** → （可选）触发前台移除 →
 * 把可搬运句柄交给 [ArchiveManager] 排队搬运。调用方只提供「条目（[LMedia]）+ 来源（[SourceId]）+ 归档点」。
 *
 * 两个入口，语义不混淆：
 * - [remove]：归档**并**让集合少一项（页面用）。来源条目必属某个可见性分区，故 `visibility` 非空；
 * - [archive]：**仅归档**，不触碰任何集合（如重复文件清理页——它自行维护列表，不走读侧投影）。
 *
 * 因此不存在"模糊可见性"：需要移除就必须给出确切分区，否则走 [archive]。
 */
object ArchiveHelper {

    private val log by lazy {
        registerLog()
    }

    // ---------------------------------------------------------------------
    // 归档 + 移除集合项
    // ---------------------------------------------------------------------

    /** 归档并让集合少一项。`visibility` 非空：来源必属某个可见性分区。 */
    suspend fun remove(
        context: Context,
        item: LMedia,
        sourceId: SourceId,
        basket: ArchiveBasket,
        visibility: MediaVisibility
    ) {
        val subject = prepare(item, sourceId) ?: return
        // 前台移除：让集合少一项（不等待后台结果）
        MediaBackends.find(sourceId)?.remove(context, visibility, item)
        // 交给编排：排队后台搬运（只传句柄）
        ArchiveManager.archive(context = context, sourceId = sourceId, subject = subject, basket = basket)
    }

    /** 归档并让集合少一项（快捷槽位版；`Other` 时弹归档选择框）。 */
    suspend fun remove(
        context: Context,
        item: LMedia,
        sourceId: SourceId,
        quick: ArchiveQuick,
        visibility: MediaVisibility,
        callback: (Boolean) -> Unit
    ) {
        val basket = quickBasket(quick)
        if (basket != null) {
            callback(true)
            remove(context, item, sourceId, basket, visibility)
            return
        }
        showArchiveDialog(context) { selected ->
            callback(true)
            Tasks.launch(Dispatchers.IO) {
                remove(context, item, sourceId, selected, visibility)
            }
        }
    }

    // ---------------------------------------------------------------------
    // 仅归档（不动集合）
    // ---------------------------------------------------------------------

    /** 仅归档：不触发任何前台移除（调用方自行维护其列表）。 */
    suspend fun archive(
        context: Context,
        item: LMedia,
        sourceId: SourceId,
        basket: ArchiveBasket
    ) {
        val subject = prepare(item, sourceId) ?: return
        ArchiveManager.archive(context = context, sourceId = sourceId, subject = subject, basket = basket)
    }

    /** 仅归档（快捷槽位版；`Other` 时弹归档选择框）。 */
    suspend fun archive(
        context: Context,
        item: LMedia,
        sourceId: SourceId,
        quick: ArchiveQuick,
        callback: (Boolean) -> Unit
    ) {
        val basket = quickBasket(quick)
        if (basket != null) {
            callback(true)
            archive(context, item, sourceId, basket)
            return
        }
        showArchiveDialog(context) { selected ->
            callback(true)
            Tasks.launch(Dispatchers.IO) {
                archive(context, item, sourceId, selected)
            }
        }
    }

    // ---------------------------------------------------------------------
    // 内部
    // ---------------------------------------------------------------------

    /** 快捷槽位 -> 归档点；`Other` 表示需弹框选择，返回 null。 */
    private fun quickBasket(quick: ArchiveQuick): ArchiveBasket? {
        return when (quick) {
            ArchiveQuick.Favorite -> ArchiveManager.favorite.value
            ArchiveQuick.Special -> ArchiveManager.special.value
            ArchiveQuick.ThumpUp -> ArchiveManager.thumpUp.value
            ArchiveQuick.Other -> null
        }
    }

    /** 锁定：按来源检索归档能力并产出可搬运句柄（集合移除**之前**调用）。 */
    private suspend fun prepare(item: LMedia, sourceId: SourceId): ArchiveSubject? {
        val backend = MediaBackends.archiver(sourceId)
        if (backend == null) {
            log.e("prepare: no archiver for sourceId = $sourceId")
            return null
        }
        val subject = backend.prepare(item)
        if (subject == null) {
            log.e("prepare: failed, mediaId = ${item.mediaId}")
        }
        return subject
    }

    private fun showArchiveDialog(context: Context, callback: (ArchiveBasket) -> Unit) {
        ArchiveSelectDialog(context, callback).show()
    }
}
