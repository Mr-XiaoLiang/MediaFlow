package com.lollipop.mediaflow.data

class MediaMetadata(
    val docId: String,
    val width: Int,
    val height: Int,
    val duration: Long,
    val lastModified: Long
) {

    companion object {
        fun formatDuration(duration: Long): String {
            if (duration <= 0) {
                return ""
            }
            val seconds = duration / 1000
            val minutes = seconds / 60
            return "$minutes:${seconds % 60}"
        }
    }

    val durationFormat: String by lazy {
        formatDuration(duration)
    }

}