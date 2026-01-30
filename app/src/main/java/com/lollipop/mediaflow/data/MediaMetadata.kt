package com.lollipop.mediaflow.data

class MediaMetadata(
    val docId: String,
    val width: Int,
    val height: Int,
    val duration: Long,
    val rotation: Int,
    val lastModified: Long,
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

        fun fromVideo(
            docId: String,
            width: Int,
            height: Int,
            duration: Long,
            lastModified: Long,
        ): MediaMetadata {
            return MediaMetadata(
                docId = docId,
                width = width,
                height = height,
                duration = duration,
                lastModified = lastModified,
                rotation = 0,
            )
        }

        fun fromImage(
            docId: String,
            width: Int,
            height: Int,
            rotation: Int,
            lastModified: Long,
        ): MediaMetadata {
            return MediaMetadata(
                docId = docId,
                width = width,
                height = height,
                duration = 0,
                rotation = rotation,
                lastModified = lastModified,
            )
        }

    }

    val durationFormat: String by lazy {
        formatDuration(duration)
    }

}