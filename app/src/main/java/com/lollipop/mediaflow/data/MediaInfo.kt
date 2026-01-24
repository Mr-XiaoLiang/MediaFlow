package com.lollipop.mediaflow.data

import android.net.Uri

class MediaRoot(
    val name: String,
    val children: List<MediaInfo>
)

sealed class MediaInfo(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val size: Long,
    val lastModified: Long,
    val path: String,
    val parentDocId: String,
    val rootUri: Uri,
    val docId: String
) {

    class Directory(
        uri: Uri,
        name: String,
        mimeType: String,
        size: Long,
        lastModified: Long,
        path: String,
        parentDocId: String,
        rootUri: Uri,
        docId: String
    ) : MediaInfo(
        uri = uri,
        name = name,
        mimeType = mimeType,
        size = size,
        lastModified = lastModified,
        path = path,
        parentDocId = parentDocId,
        rootUri = rootUri,
        docId = docId
    ) {
        val children = mutableListOf<MediaInfo>()
    }

    class File(
        uri: Uri,
        name: String,
        mimeType: String,
        size: Long,
        lastModified: Long,
        path: String,
        parentDocId: String,
        rootUri: Uri,
        docId: String,
        val mediaType: MediaType
    ) : MediaInfo(
        uri = uri,
        name = name,
        mimeType = mimeType,
        size = size,
        lastModified = lastModified,
        path = path,
        parentDocId = parentDocId,
        rootUri = rootUri,
        docId = docId
    ) {

        var metadata: MediaMetadata? = null

    }

}