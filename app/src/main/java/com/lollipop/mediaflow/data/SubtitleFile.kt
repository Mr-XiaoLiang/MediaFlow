package com.lollipop.mediaflow.data

import android.net.Uri
import androidx.media3.common.MimeTypes
import java.io.File

class SubtitleFile(
    val uri: Uri,
    val name: String,
    val rootUri: Uri,
    val docId: String,
) {

    companion object {

        private fun isSubtitleFile(name: String): Boolean {
            val file = File(name)
            val suffix = file.extension.lowercase()
            val mimeType = MimeType.find(suffix)
            return mimeType != null
        }

        fun parse(uri: Uri, name: String, rootUri: Uri, docId: String): SubtitleFile? {
            if (isSubtitleFile(name)) {
                return SubtitleFile(uri, name, rootUri, docId)
            }
            return null
        }
    }

    private val parsedComponents by lazy {
        parseNameComponents()
    }

    /**
     * 获取字幕格式后缀（如 "srt", "vtt"）
     */
    val suffix: String
        get() {
            return parsedComponents.suffix
        }

    /**
     * 获取语言代码（如 "en"），如果没有则返回 null
     */
    val language: String
        get() {
            return parsedComponents.language
        }

    /**
     * 获取文件名主体部分（不含语言后缀和扩展名）
     */
    val baseName: String
        get() {
            return parsedComponents.baseName
        }

    /**
     * 媒体类型
     */
    val mimeType: MimeType?
        get() {
            return parsedComponents.mimeType
        }

    private data class ParsedComponents(
        val baseName: String,
        val language: String,
        val suffix: String,
        val mimeType: MimeType?
    )

    private fun parseNameComponents(): ParsedComponents {
        val tempFile = File(name)
        val nameWithoutExt = tempFile.nameWithoutExtension
        val fileSuffix = tempFile.extension.lowercase()
        // 按 "." 分割，尝试匹配语言代码
        val parts = nameWithoutExt.split(".")
        // 至少需要 2 部分才能有语言后缀（base + language）
        if (parts.size >= 2) {
            val possibleLang = parts.last()
            val baseName = parts.dropLast(1).joinToString(".")
            return ParsedComponents(
                baseName = baseName,
                language = possibleLang.lowercase(),
                suffix = fileSuffix,
                mimeType = MimeType.find(fileSuffix)
            )
        }
        return ParsedComponents(
            baseName = nameWithoutExt,
            language = "",
            suffix = fileSuffix,
            mimeType = MimeType.find(fileSuffix)
        )
    }

    /**
     * 后缀名	对应 MimeType 变量	字符串值	说明
     * .srt	MimeTypes.APPLICATION_SUBRIP	application/x-subrip	最常见的纯文本字幕
     * .vtt	MimeTypes.TEXT_VTT	text/vtt	Web 标准字幕，常见于 HLS/Dash 流
     * .ass / .ssa	MimeTypes.TEXT_SSA	text/x-ssa	支持特效、样式、位置自定义的字幕
     * .ttml / .xml	MimeTypes.APPLICATION_TTML	application/ttml+xml	电视广播常用格式
     *
     */
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
            /**
             * 根据文件名或后缀获取对应的 MimeType 字符串
             * 如果匹配不到，默认返回 TEXT_UNKNOWN 让播放器尝试自适应
             */
            fun find(extension: String?): MimeType? {
                val ext = extension?.lowercase() ?: return null
                return entries.find { it.suffixes.contains(ext) }
            }
        }
    }

}