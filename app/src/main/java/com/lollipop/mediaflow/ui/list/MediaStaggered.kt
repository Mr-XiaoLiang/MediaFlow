package com.lollipop.mediaflow.ui.list

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.window.layout.WindowMetricsCalculator
import com.bumptech.glide.Glide
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MediaMetadata
import com.lollipop.mediaflow.data.MetadataLoader
import com.lollipop.mediaflow.databinding.ItemMediaStaggeredBinding
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.mediaflow.ui.view.RatioFrameLayout
import kotlinx.coroutines.Job

object MediaStaggered : BasicListDelegate() {

    fun <T : RecyclerView.Adapter<*>> buildLiningEdge(contentAdapter: T): LiningEdgeAdapter<T> {
        return LiningEdgeAdapter(contentAdapter) { SpaceAdapter() }
    }

    fun <T : BasicItemAdapter<*>> buildDelegate(contentAdapter: T): Delegate<T> {
        return Delegate(buildLiningEdge(contentAdapter))
    }

    fun updateSpanCountVertical(
        layoutManager: StaggeredGridLayoutManager,
        activity: Activity?,
        itemWidthDp: Int,
        minCount: Int = 1,
        maxCount: Int = 5
    ) {
        val act = activity ?: return
        // 获取当前窗口度量值
        val windowMetrics =
            WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(act)
        val widthPx = windowMetrics.bounds.width()

        // 转换 px 为 dp 以适配不同密度
        val density = activity.resources.displayMetrics.density
        val widthDp = widthPx / density

        // 除以150，420DP宽度的时候，预计3列，横屏的时候6列
        val columnCount = ((widthDp / itemWidthDp) + 0.5F).toInt()
            .coerceAtLeast(minCount)
            .coerceAtMost(maxCount)
        layoutManager.spanCount = columnCount
    }

    fun updateSpanCountHorizontal(
        layoutManager: StaggeredGridLayoutManager,
        activity: Activity?,
        itemHeightDp: Int,
        minCount: Int = 1,
        maxCount: Int = 5
    ) {
        val act = activity ?: return
        // 获取当前窗口度量值
        val windowMetrics =
            WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(act)
        val heightPx = windowMetrics.bounds.height()

        // 转换 px 为 dp 以适配不同密度
        val density = activity.resources.displayMetrics.density
        val heightDp = heightPx / density

        // 除以150，420DP宽度的时候，预计3列，横屏的时候6列
        val columnCount = ((heightDp / itemHeightDp) + 0.5F).toInt()
            .coerceAtLeast(minCount)
            .coerceAtMost(maxCount)
        layoutManager.spanCount = columnCount
    }

    class Delegate<T : BasicItemAdapter<*>>(
        private val adapterHolder: LiningEdgeAdapter<T>
    ) {

        private var layoutManager: StaggeredGridLayoutManager? = null

        fun bind(recyclerView: RecyclerView, activity: Activity?, itemWidthDp: Int = 150) {
            layoutManager = StaggeredGridLayoutManager(2, RecyclerView.VERTICAL)
            recyclerView.layoutManager = layoutManager
            recyclerView.adapter = adapterHolder.root
            updateSpanCount(activity)
        }

        fun updateSpanCount(activity: Activity?, itemWidthDp: Int = 150) {
            layoutManager?.let {
                updateSpanCountVertical(it, activity, itemWidthDp)
            }
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
        protected val onItemClick: ItemClick
    ) : BasicItemAdapter<MediaItemHolder>(data = data) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaItemHolder {
            return MediaItemHolder(
                ItemMediaStaggeredBinding.inflate(getLayoutInflater(parent), parent, false),
                onItemClick
            )
        }

        override fun onBindViewHolder(holder: MediaItemHolder, position: Int) {
            holder.bind(data[position])
        }

    }

    class SpaceAdapter : BasicSpaceAdapter() {
        override fun createSpaceView(context: Context): View {
            return FullSpanSpace(context)
        }
    }

    private class FullSpanSpace(context: Context) : View(context) {
        init {
            isInvisible = true
        }

        override fun draw(canvas: Canvas) {
            if (isVisible) {
                super.draw(canvas)
            }
        }

        override fun setLayoutParams(params: ViewGroup.LayoutParams?) {
            if (params is StaggeredGridLayoutManager.LayoutParams) {
                params.isFullSpan = true
            }
            super.setLayoutParams(params)
        }
    }

    open class MediaItemHolder(
        val binding: ItemMediaStaggeredBinding,
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
            val ratioWidth = metadata?.width ?: 1
            var ratioHeight = metadata?.height ?: 1
            if (ratioHeight < ratioWidth) {
                ratioHeight = ratioWidth
            }
            if (ratioHeight * 9F / 16F > ratioWidth) {
                ratioHeight = (ratioWidth / 9F * 16F).toInt()
            }
            binding.ratioLayout.setRatio(ratioWidth, ratioHeight, RatioFrameLayout.Mode.WidthFirst)
        }
    }


}