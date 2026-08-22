package com.lollipop.mediaflow.data.common

import com.lollipop.mediaflow.data.LMedia

/**
 * 媒体排序（跨来源通用，基于 LMedia 契约）。
 * 从 local/MediaSort 迁移并解耦：原实现绑定 MediaInfo.File，现改为基于 LMedia，
 * 使 Local / WebDAV / Jellyfin 等来源均可复用。
 */
sealed class MediaSort(val key: String) {

    companion object {
        fun findByKey(key: String): MediaSort? {
            return when (key) {
                DateAsc.key -> DateAsc
                NameAsc.key -> NameAsc
                DateDesc.key -> DateDesc
                NameDesc.key -> NameDesc
                Random.key -> Random
                else -> null
            }
        }
    }

    abstract fun sort(fileList: ArrayList<out LMedia>)

    object DateAsc : MediaSort(key = "date_asc") {
        override fun sort(fileList: ArrayList<out LMedia>) {
            fileList.sortBy { it.lastModified }
        }
    }

    object NameAsc : MediaSort(key = "name_asc") {
        override fun sort(fileList: ArrayList<out LMedia>) {
            fileList.sortBy { it.name }
        }
    }

    object DateDesc : MediaSort(key = "date_desc") {
        override fun sort(fileList: ArrayList<out LMedia>) {
            fileList.sortByDescending { it.lastModified }
        }
    }

    object NameDesc : MediaSort(key = "name_desc") {
        override fun sort(fileList: ArrayList<out LMedia>) {
            fileList.sortByDescending { it.name }
        }
    }

    object Random : MediaSort(key = "random") {
        override fun sort(fileList: ArrayList<out LMedia>) {
            fileList.shuffle()
        }
    }

}
