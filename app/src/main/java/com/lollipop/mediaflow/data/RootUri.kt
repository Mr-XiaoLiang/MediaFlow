package com.lollipop.mediaflow.data

import android.net.Uri

class RootUri(
    val uri: Uri,
    val visibility: MediaVisibility,
    val name: String
) {

    val uriString by lazy { uri.toString() }

    fun changeVisibility(v: MediaVisibility): RootUri {
        return RootUri(uri = uri, visibility = v, name = name)
    }

}