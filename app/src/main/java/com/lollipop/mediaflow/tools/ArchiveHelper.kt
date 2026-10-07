package com.lollipop.mediaflow.tools

import android.content.Context
import com.lollipop.common.tools.Tasks
import com.lollipop.mediaflow.data.local.ArchiveBasket
import com.lollipop.mediaflow.data.local.ArchiveManager
import com.lollipop.mediaflow.data.local.ArchiveQuick
import com.lollipop.mediaflow.data.local.MediaInfo
import com.lollipop.mediaflow.data.local.LocalGallery
import com.lollipop.mediaflow.page.archive.ArchiveSelectDialog
import kotlinx.coroutines.Dispatchers

object ArchiveHelper {

    suspend fun remove(
        context: Context,
        file: MediaInfo.File,
        basket: ArchiveBasket,
        gallery: LocalGallery?
    ) {
        gallery?.remove(file)
        ArchiveManager.moveToArchive(context = context, basket = basket, mediaInfo = file)
    }

    suspend fun remove(
        context: Context,
        file: MediaInfo.File,
        quick: ArchiveQuick,
        gallery: LocalGallery?,
        callback: (Boolean) -> Unit
    ) {
        val basket = when (quick) {
            ArchiveQuick.Favorite -> {
                ArchiveManager.favorite.value
            }

            ArchiveQuick.Special -> {
                ArchiveManager.special.value
            }

            ArchiveQuick.ThumpUp -> {
                ArchiveManager.thumpUp.value
            }

            ArchiveQuick.Other -> {
                null
            }
        }
        if (basket != null) {
            callback(true)
            remove(context, file, basket, gallery)
            return
        }
        showArchiveDialog(context) {
            callback(true)
            Tasks.launch(Dispatchers.IO) {
                remove(context, file, it, gallery)
            }
        }
    }

    private fun showArchiveDialog(context: Context, callback: (ArchiveBasket) -> Unit) {
        ArchiveSelectDialog(context, callback).show()
    }

}