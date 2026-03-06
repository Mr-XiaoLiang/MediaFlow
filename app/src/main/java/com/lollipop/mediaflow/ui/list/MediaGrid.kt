package com.lollipop.mediaflow.ui.list

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Space
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.window.layout.WindowMetricsCalculator
import com.bumptech.glide.Glide
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MediaMetadata
import com.lollipop.mediaflow.data.MetadataLoader
import com.lollipop.mediaflow.databinding.ItemMediaGridBinding
import com.lollipop.mediaflow.tools.Preferences
import kotlinx.coroutines.Job

object MediaGrid : BasicListDelegate() {

    fun <T : RecyclerView.Adapter<*>> buildLiningEdge(contentAdapter: T): LiningEdgeAdapter<T> {
        return LiningEdgeAdapter(contentAdapter) { SpaceAdapter() }
    }

    fun <T : BasicItemAdapter<*>> buildDelegate(contentAdapter: T): Delegate<T> {
        return Delegate(buildLiningEdge(contentAdapter))
    }

    fun bindEdgeSpanSizeLookup(recyclerView: RecyclerView) {
        val layoutManager = recyclerView.layoutManager
        if (layoutManager is GridLayoutManager) {
            layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    val concatAdapter = recyclerView.adapter ?: return 1
                    val spanCount = layoutManager.spanCount
                    if (position == 0) {
                        return spanCount
                    }
                    val itemCount = concatAdapter.itemCount
                    if (position == itemCount - 1) {
                        return spanCount
                    }
                    return 1
                }
            }
        }
    }

    class Delegate<T : BasicItemAdapter<*>>(
        private val adapterHolder: LiningEdgeAdapter<T>
    ) {

        private var layoutManager: GridLayoutManager? = null

        fun bind(recyclerView: RecyclerView, activity: Activity?) {
            layoutManager = GridLayoutManager(recyclerView.context, 1)
            recyclerView.layoutManager = layoutManager
            recyclerView.adapter = adapterHolder.root
            bindEdgeSpanSizeLookup(recyclerView)
            updateSpanCount(activity)
        }

        fun updateSpanCount(activity: Activity?) {
            val act = activity ?: return
            // 获取当前窗口度量值
            val windowMetrics =
                WindowMetricsCalculator.Companion.getOrCreate().computeCurrentWindowMetrics(act)
            val widthPx = windowMetrics.bounds.width()

            // 转换 px 为 dp 以适配不同密度
            val density = activity.resources.displayMetrics.density
            val widthDp = widthPx / density

            val columnCount = (widthDp / 80).toInt().coerceAtLeast(1)
            layoutManager?.spanCount = columnCount
        }

        fun onInsetsChanged(top: Int, bottom: Int) {
            adapterHolder.startSpace.setSpacePx(top)
            adapterHolder.endSpace.setSpacePx(bottom)
        }

        @SuppressLint("NotifyDataSetChanged")
        fun notifyContentDataSetChanged() {
            adapterHolder.content.notifyDataSetChanged()
        }

    }

    open class ItemAdapter(
        data: List<MediaInfo.File>,
        onItemClick: ItemClick
    ) : BasicItemAdapter<MediaItemHolder>(data = data, onItemClick = onItemClick) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaItemHolder {
            return MediaItemHolder(
                ItemMediaGridBinding.inflate(getLayoutInflater(parent), parent, false),
                onItemClick
            )
        }

        override fun onBindViewHolder(holder: MediaItemHolder, position: Int) {
            holder.bind(data[position])
        }

    }

    class SpaceAdapter : BasicSpaceAdapter() {
        override fun createSpaceView(context: Context): View {
            return Space(context)
        }
    }

    open class MediaItemHolder(
        val binding: ItemMediaGridBinding,
        val onClickCallback: ItemClick
    ) : RecyclerView.ViewHolder(binding.root) {

        private var loadingJob: Job? = null

        init {
            itemView.setOnClickListener {
                when (onClickCallback) {
                    is ItemClick.OpenByIndex -> {
                        onClickCallback.callback(bindingAdapterPosition)
                    }

                    is ItemClick.OpenByType -> {
                        if (Preferences.isQuickPlayEnable.get()) {
                            onClickCallback.callback(
                                bindingAdapterPosition,
                                Preferences.quickPlayMode.get()
                            )
                        } else {
                            OptionTypePopup.show(itemView) { type ->
                                onClickCallback.callback(bindingAdapterPosition, type)
                            }
                        }
                    }
                }
            }
            if (onClickCallback is ItemClick.OpenByType) {
                itemView.setOnLongClickListener {
                    if (Preferences.isQuickPlayEnable.get()) {
                        OptionTypePopup.show(itemView) { type ->
                            onClickCallback.callback(bindingAdapterPosition, type)
                        }
                        return@setOnLongClickListener true
                    } else {
                        return@setOnLongClickListener false
                    }
                }
            }
        }

        fun bind(mediaInfo: MediaInfo.File) {
            Glide.with(itemView)
                .load(mediaInfo.uri)
                .into(binding.mediaPreview)
            loadingJob?.cancel()
            loadingJob = MetadataLoader.load(itemView.context, mediaInfo) { metadata ->
                if (metadata != null) {
                    updateUI(metadata)
                }
            }
        }

        fun updateUI(metadata: MediaMetadata?) {
            val duration = metadata?.duration ?: 0
            if (duration > 0) {
                binding.durationView.isVisible = true
                binding.durationView.text = metadata?.durationFormat ?: ""
            } else {
                binding.durationView.isVisible = false
            }
        }
    }
}