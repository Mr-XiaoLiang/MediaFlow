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
            var seconds = duration / 1000
            val minutes = seconds / 60
            seconds %= 60

            val builder = StringBuilder()

            if (minutes < 10) {
                builder.append("0")
            }
            builder.append(minutes)
            builder.append(":")
            if (seconds < 10) {
                builder.append("0")
            }
            builder.append(seconds)
            return builder.toString()
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

    val sizeFormat: String by lazy {
        "$width * $height"
    }

    val needRotate: Boolean = rotation == 90 || rotation == 270

}