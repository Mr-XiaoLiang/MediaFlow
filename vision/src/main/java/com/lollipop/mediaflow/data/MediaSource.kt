package com.lollipop.mediaflow.data

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.lollipop.mediaflow.data.common.MediaSort

sealed class MediaSource {

    val local = SnapshotStateList<LMedia>()
    val webDAV = SnapshotStateList<LMedia>()

    object PublicVideo : MediaSource()
    object PrivateVideo : MediaSource()
    object PublicImage : MediaSource()
    object PrivateImage : MediaSource()

}


sealed class LocalState {

    private val sortState = mutableStateOf<MediaSort>(MediaSort.DateDesc)
    private val selectedFolderIdState = mutableStateOf("")

    val sort: State<MediaSort>
        get() {
            return sortState
        }

    val selectedFolderId: State<String>
        get() {
            return selectedFolderIdState
        }

    object Image : LocalState()
    object Video : LocalState()

}

