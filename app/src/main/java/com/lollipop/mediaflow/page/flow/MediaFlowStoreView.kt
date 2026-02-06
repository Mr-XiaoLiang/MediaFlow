package com.lollipop.mediaflow.page.flow

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.ui.MediaGridAdapter

class MediaFlowStoreView(
    private val onItemClick: (MediaInfo.File, Int) -> Unit
) {

    private val mediaData = ArrayList<MediaInfo.File>()

    private var recyclerView: RecyclerView? = null

    private val gridAdapterDelegate by lazy {
        MediaGridAdapter.buildDelegate(
            MediaGridAdapter.MediaItemAdapter(
                mediaData, onItemClick
            )
        )
    }

    fun getView(context: Context): View {
        val oldView = recyclerView
        if (oldView != null) {
            return oldView
        }
        val newView = RecyclerView(context)
        recyclerView = newView
        val activity = context as? Activity
        gridAdapterDelegate.bind(newView, activity)
        return newView
    }

    @SuppressLint("NotifyDataSetChanged")
    fun resetData(data: List<MediaInfo.File>) {
        mediaData.clear()
        mediaData.addAll(data)
        gridAdapterDelegate.notifyContentDataSetChanged()
    }

    fun onInsetsChanged(left: Int, top: Int, right: Int, bottom: Int) {
        gridAdapterDelegate.onInsetsChanged(top, bottom)
        recyclerView?.setPadding(left, 0, right, 0)
    }

    fun updateSpanCount(context: Activity?) {
        gridAdapterDelegate.updateSpanCount(context)
    }

}