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

    /** 该媒体的字幕集合；空集合表示无字幕（视频不一定有字幕，图片通常为空）。 */
    val subtitleList: List<LSubtitle>

    fun loadMetadata(context: Context, callback: (MediaMetadata?) -> Unit)

}