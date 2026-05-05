package com.lollipop.auditory.data

class AudioInfo(
    val id: Long,
    val title: String
) {

    val titleHash = title.hashCode()

    companion object {
        val EMPTY = AudioInfo(id = 0, title = "")
    }

}