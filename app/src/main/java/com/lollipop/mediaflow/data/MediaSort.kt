package com.lollipop.mediaflow.data

sealed class MediaSort {

    abstract fun sort(fileList: ArrayList<MediaInfo.File>)

    object DateAsc : MediaSort() {
        override fun sort(fileList: ArrayList<MediaInfo.File>) {
            fileList.sortBy { it.lastModified }
        }
    }
    object NameAsc : MediaSort() {
        override fun sort(fileList: ArrayList<MediaInfo.File>) {
            fileList.sortBy { it.name }
        }
    }
    object DateDesc : MediaSort() {
        override fun sort(fileList: ArrayList<MediaInfo.File>) {
            fileList.sortByDescending { it.lastModified }
        }
    }
    object NameDesc : MediaSort() {
        override fun sort(fileList: ArrayList<MediaInfo.File>) {
            fileList.sortByDescending { it.name }
        }
    }

    object Random : MediaSort() {
        override fun sort(fileList: ArrayList<MediaInfo.File>) {
            fileList.shuffle()
        }
    }

}