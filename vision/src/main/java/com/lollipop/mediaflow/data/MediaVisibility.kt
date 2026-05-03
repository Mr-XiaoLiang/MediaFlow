package com.lollipop.mediaflow.data

enum class MediaVisibility(val key: String) {
    Public(key = "public"),
    Private(key = "private");

    companion object {
        fun findByKey(key: String): MediaVisibility {
            return entries.firstOrNull { it.key == key } ?: Public
        }
    }

}