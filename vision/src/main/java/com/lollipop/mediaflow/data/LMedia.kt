package com.lollipop.mediaflow.data

import android.content.Context
import android.net.Uri
import com.lollipop.mediaflow.data.local.MediaType

interface LMedia {

    val uri: Uri
    val name: String
    val mediaType: MediaType
    val mimeType: String
    val size: Long
    val lastModified: Long
    val mediaId: String

    fun loadMetadata(context: Context, callback: (MediaMetadata?) -> Unit)

}