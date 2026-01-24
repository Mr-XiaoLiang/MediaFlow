package com.lollipop.mediaflow.data

enum class MediaType(val mimeType: String, val mimePrefix: String, val dataKey: String) {
    Image("image/*", "image/", "image"),
    Video("video/*", "video/", "video"),
}