package com.lollipop.mediaflow.tools

object DisplayFormater {

    private const val MINUTES = 60 * 1000L
    private const val SECONDS = 1000L

    fun formatTime(progress: Long): String {
        if (progress == 0L) {
            return "00:00"
        }
        val progressMinutes = progress / MINUTES
        val progressSeconds = (progress / SECONDS) % 60
        val builder = StringBuilder()
        if (progressMinutes < 10) {
            builder.append("0")
        }
        builder.append(progressMinutes).append(":")
        if (progressSeconds < 10) {
            builder.append("0")
        }
        builder.append(progressSeconds)
        return builder.toString()
    }

    fun formatTime(progress: Long, total: Long): String {
        val builder = StringBuilder()
        if (progress == 0L || total == 0L) {
            builder.append("00:00")
        } else {
            val progressMinutes = progress / MINUTES
            val progressSeconds = (progress / SECONDS) % 60
            if (progressMinutes < 10) {
                builder.append("0")
            }
            builder.append(progressMinutes).append(":")
            if (progressSeconds < 10) {
                builder.append("0")
            }
            builder.append(progressSeconds)
        }
        builder.append(" / ")
        if (total == 0L) {
            builder.append("00:00")
        } else {
            val totalMinutes = total / MINUTES
            val totalSeconds = (total / SECONDS) % 60
            if (totalMinutes < 10) {
                builder.append("0")
            }
            builder.append(totalMinutes).append(":")
            if (totalSeconds < 10) {
                builder.append("0")
            }
            builder.append(totalSeconds)
        }
        return builder.toString()
    }

}