package com.lollipop.mediaflow.tools

import android.content.Context
import com.lollipop.mediaflow.data.ArchiveBasket
import com.lollipop.mediaflow.data.ArchiveManager
import com.lollipop.mediaflow.data.ArchiveQuick
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MediaStore
import com.lollipop.mediaflow.page.archive.ArchiveSelectDialog

object ArchiveHelper {

    fun remove(
        context: Context,
        file: MediaInfo.File,
        basket: ArchiveBasket,
        gallery: MediaStore.Gallery?
    ) {
        gallery?.remove(file)
        ArchiveManager.moveToArchive(context = context, basket = basket, mediaInfo = file)
    }

    fun remove(
        context: Context,
        file: MediaInfo.File,
        quick: ArchiveQuick,
        gallery: MediaStore.Gallery?,
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
            remove(context, file, it, gallery)
        }
    }

    private fun showArchiveDialog(context: Context, callback: (ArchiveBasket) -> Unit) {
        ArchiveSelectDialog(context, callback).show()
    }

}