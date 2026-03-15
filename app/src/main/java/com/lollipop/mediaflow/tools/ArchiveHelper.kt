package com.lollipop.mediaflow.tools

import android.content.Context
import com.lollipop.mediaflow.data.ArchiveManager
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MediaStore

object ArchiveHelper {

    fun remove(context: Context, file: MediaInfo.File, gallery: MediaStore.Gallery?) {
        gallery?.remove(file)
        ArchiveManager.moveToArchive(context, file)
    }

}