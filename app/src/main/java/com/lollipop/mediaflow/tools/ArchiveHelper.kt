package com.lollipop.mediaflow.tools

import android.content.Context
import com.lollipop.common.tools.Tasks
import com.lollipop.mediaflow.data.local.ArchiveBasket
import com.lollipop.mediaflow.data.local.ArchiveManager
import com.lollipop.mediaflow.data.local.ArchiveQuick
import com.lollipop.mediaflow.data.local.MediaInfo
import com.lollipop.mediaflow.data.local.MediaVisibility
import com.lollipop.mediaflow.data.source.MediaBackends
import com.lollipop.mediaflow.page.archive.ArchiveSelectDialog
import kotlinx.coroutines.Dispatchers

/**
 * 归档 / 移除帮助类。
 *
 * 移除动作统一走 [MediaBackends]（唯一写路径）：后端负责更新共享 Catalog，
 * 视图随之重算，避免此前「Gallery 副本 + Store」双写导致的短暂不一致。
 *
 * [visibility] 为全局维度，由调用方（当前页面）提供；为 null 时表示只归档不移除
 * （例如重复文件清理页并不从库里移除投影）。
 */
object ArchiveHelper {

    suspend fun remove(
        context: Context,
        file: MediaInfo.File,
        basket: ArchiveBasket,
        visibility: MediaVisibility?
    ) {
        if (visibility != null) {
            MediaBackends.local.remove(context, visibility, file)
        }
        ArchiveManager.moveToArchive(context = context, basket = basket, mediaInfo = file)
    }

    suspend fun remove(
        context: Context,
        file: MediaInfo.File,
        quick: ArchiveQuick,
        visibility: MediaVisibility?,
        callback: (Boolean) -> Unit
    ) {
        val basket = when (quick) {
            ArchiveQuick.Favorite -> ArchiveManager.favorite.value
            ArchiveQuick.Special -> ArchiveManager.special.value
            ArchiveQuick.ThumpUp -> ArchiveManager.thumpUp.value
            ArchiveQuick.Other -> null
        }
        if (basket != null) {
            callback(true)
            remove(context, file, basket, visibility)
            return
        }
        showArchiveDialog(context) {
            callback(true)
            Tasks.launch(Dispatchers.IO) {
                remove(context, file, it, visibility)
            }
        }
    }

    private fun showArchiveDialog(context: Context, callback: (ArchiveBasket) -> Unit) {
        ArchiveSelectDialog(context, callback).show()
    }
}
