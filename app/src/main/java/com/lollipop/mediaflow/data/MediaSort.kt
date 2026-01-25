package com.lollipop.mediaflow.data

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

    abstract fun sort(fileList: ArrayList<MediaInfo.File>)

    object DateAsc : MediaSort(key = "date_asc") {
        override fun sort(fileList: ArrayList<MediaInfo.File>) {
            fileList.sortBy { it.lastModified }
        }
    }

    object NameAsc : MediaSort(key = "name_asc") {
        override fun sort(fileList: ArrayList<MediaInfo.File>) {
            fileList.sortBy { it.name }
        }
    }

    object DateDesc : MediaSort(key = "date_desc") {
        override fun sort(fileList: ArrayList<MediaInfo.File>) {
            fileList.sortByDescending { it.lastModified }
        }
    }

    object NameDesc : MediaSort(key = "name_desc") {
        override fun sort(fileList: ArrayList<MediaInfo.File>) {
            fileList.sortByDescending { it.name }
        }
    }

    object Random : MediaSort(key = "random") {
        override fun sort(fileList: ArrayList<MediaInfo.File>) {
            fileList.shuffle()
        }
    }

}