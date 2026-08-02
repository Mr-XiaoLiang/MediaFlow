package com.lollipop.mediaflow.page.flow.video

import android.view.View
import com.lollipop.common.ui.view.FlowPlayerGestureHost
import com.lollipop.mediaflow.data.ArchiveManager
import com.lollipop.mediaflow.data.ArchiveQuick
import com.lollipop.mediaflow.ui.PipVisibleFilter

class ArchiveDelegate(
    private val onClick: (ArchiveQuick) -> Unit
) {

    private val btnHolders = mutableListOf<BtnHolder>()

    fun register(view: View, tag: ArchiveQuick) {
        btnHolders.add(BtnHolder(tag, view, onClick))
    }

    fun updateArchive(archiveEnable: Boolean) {
        for (holder in btnHolders) {
            holder.visibleFilter.preference.setVisible(isArchiveEnable(archiveEnable, holder.tag))
        }
    }

    fun registerPenetrate(group: FlowPlayerGestureHost) {
        for (holder in btnHolders) {
            group.registerPenetrate(holder.view)
        }
    }

    private fun isArchiveEnable(archiveEnable: Boolean, quick: ArchiveQuick): Boolean {
        return archiveEnable && ArchiveManager.isQuickEnable(quick)
    }

    class BtnHolder(val tag: ArchiveQuick, val view: View, onClick: (ArchiveQuick) -> Unit) {

        val visibleFilter = PipVisibleFilter(view)

        init {
            view.setOnClickListener {
                onClick(tag)
            }
        }

    }

}