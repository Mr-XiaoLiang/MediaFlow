package com.lollipop.mediaflow.data.common

import java.util.Locale

/**
 * 文件大小格式化工具（跨来源通用）。
 * 原实现位于 local/MediaInfo，现抽至 common 供 Local / WebDAV 等统一使用。
 */
object FileSizeFormatter {

    private val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")

    fun format(sizeBytes: Long): String {
        if (sizeBytes <= 0) {
            return "0 B"
        }
        var digitSize = sizeBytes.toDouble()
        var unitIndex = 0
        while (digitSize >= 1000 && unitIndex < units.size - 1) {
            digitSize /= 1000.0
            unitIndex++
        }
        return String.format(Locale.US, "%.1f %s", digitSize, units[unitIndex])
    }

}
