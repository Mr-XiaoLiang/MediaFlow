package com.lollipop.mediaflow.data.common

import androidx.media3.common.MimeTypes
import java.io.File

/**
 * 字幕解析与类型定义（跨来源通用）。
 * 从 local/SubtitleFile 抽取的核心算法，去除来源专属字段（uri / rootUri / docId / videoId），
 * 仅保留解析结果（baseName / language / suffix / mimeType）。
 * 各来源可在其专属字幕类中包装本类并附加来源字段。
 */
class SubtitleInfo(
    val name: String,
    val baseName: String,
    val language: String,
    val suffix: String,
    val mimeType: MimeType?
) {

    companion object {

        fun isSubtitleFile(name: String): Boolean {
            return MimeType.find(File(name).extension.lowercase()) != null
        }

        /**
         * 解析字幕名，无法识别为字幕时返回 null。
         */
        fun parse(name: String): SubtitleInfo? {
            val tempFile = File(name)
            val fileSuffix = tempFile.extension.lowercase()
            val mimeType = MimeType.find(fileSuffix) ?: return null
            val nameWithoutExt = tempFile.nameWithoutExtension
            val parts = nameWithoutExt.split(".")
            return if (parts.size >= 2) {
                val possibleLang = parts.last()
                val baseName = parts.dropLast(1).joinToString(".")
                SubtitleInfo(
                    name = name,
                    baseName = baseName,
                    language = possibleLang.lowercase(),
                    suffix = fileSuffix,
                    mimeType = mimeType
                )
            } else {
                SubtitleInfo(
                    name = name,
                    baseName = nameWithoutExt,
                    language = "",
                    suffix = fileSuffix,
                    mimeType = mimeType
                )
            }
        }
    }

    enum class MimeType(
        val mime: String,
        val suffixes: Array<String>
    ) {
        SRT(mime = MimeTypes.APPLICATION_SUBRIP, suffixes = arrayOf("srt")),
        VTT(mime = MimeTypes.TEXT_VTT, suffixes = arrayOf("vtt")),
        SSA(mime = MimeTypes.TEXT_SSA, suffixes = arrayOf("ass", "ssa")),
        TTML(mime = MimeTypes.APPLICATION_TTML, suffixes = arrayOf("ttml", "xml", "dfxp")),
        VOBSUB(mime = MimeTypes.APPLICATION_VOBSUB, suffixes = arrayOf("idx", "sub"));

        companion object {
            fun find(extension: String?): MimeType? {
                val ext = extension?.lowercase() ?: return null
                return entries.find { it.suffixes.contains(ext) }
            }
        }
    }

}
