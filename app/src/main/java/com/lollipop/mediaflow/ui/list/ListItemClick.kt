package com.lollipop.mediaflow.ui.list

import com.lollipop.mediaflow.data.MediaLayout

sealed class ItemClick {
    class OpenByType(
        val callback: (Int, MediaLayout) -> Unit
    ) : ItemClick()

    class OpenByIndex(
        val callback: (Int) -> Unit
    ) : ItemClick()
}