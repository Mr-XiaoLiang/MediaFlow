package com.lollipop.mediaflow.data.local

import android.net.Uri
import com.lollipop.mediaflow.data.common.SubtitleInfo

/**
 * Local 专属字幕类。
 * 解析算法（baseName / language / suffix / mimeType）已抽到 common/SubtitleInfo，
 * 此处仅包装来源专属字段（uri / rootUri / docId / videoId）。
 */
class SubtitleFile(
    val uri: Uri,
    val name: String,
    val rootUri: Uri,
    val docId: String
) {

    var videoId: String = ""

    private val parsed by lazy {
        SubtitleInfo.parse(name)
    }

    val suffix: String
        get() = parsed?.suffix ?: ""

    val language: String
        get() = parsed?.language ?: ""

    val baseName: String
        get() = parsed?.baseName ?: name

    val mimeType: String?
        get() = parsed?.mimeType?.mime

    companion object {

        fun parse(uri: Uri, name: String, rootUri: Uri, docId: String): SubtitleFile? {
            if (SubtitleInfo.isSubtitleFile(name)) {
                return SubtitleFile(uri, name, rootUri, docId)
            }
            return null
        }
    }

}
