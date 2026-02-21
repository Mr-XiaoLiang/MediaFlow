package com.lollipop.mediaflow.data

enum class MediaLayout(val dataKey: String) {
    Flow(dataKey = "flow"),
    Gallery(dataKey = "gallery");

    companion object {
        fun findByKey(key: String): MediaLayout? {
            return entries.firstOrNull {
                it.dataKey == key
            }
        }
    }

}