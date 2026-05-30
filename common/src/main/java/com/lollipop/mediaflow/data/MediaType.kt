package com.lollipop.mediaflow.data

enum class MediaType(val mimeType: String, val mimePrefix: String, val dataKey: String) {
    Image("image/*", "image/", "image"),
    Video("video/*", "video/", "video");

    companion object {
        fun findByKey(key: String): MediaType? {
            return entries.firstOrNull {
                it.dataKey == key
            }
        }
    }

}