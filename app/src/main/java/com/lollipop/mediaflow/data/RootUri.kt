package com.lollipop.mediaflow.data

import android.net.Uri

class RootUri(
    val uri: Uri,
    val visibility: MediaVisibility,
    val name: String
) {

    val uriString by lazy { uri.toString() }

}