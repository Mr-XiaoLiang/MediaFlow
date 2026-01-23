package com.lollipop.mediaflow.data

import android.net.Uri

class MediaStore(
    private val key: String
) {

    private val uriList = mutableSetOf<Uri>()

    fun add(uri: Uri) {
        uriList.add(uri)
        save()
    }

    fun remove(uri: Uri) {
        uriList.remove(uri)
        save()
    }

    fun load() {
        TODO()
    }

    private fun save() {
        TODO()
    }

}