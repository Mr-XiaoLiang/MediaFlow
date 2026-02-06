package com.lollipop.mediaflow.ui

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Space
import androidx.collection.LruCache
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MediaMetadata
import com.lollipop.mediaflow.databinding.ItemMediaGridBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

abstract class MediaGridFragment : InsetsFragment() {

    protected fun <T : MediaItemAdapter> buildLiningEdge(contentAdapter: T): LiningEdgeEdgeAdapter<T> {
        return LiningEdgeEdgeAdapter(contentAdapter)
    }

    protected class LiningEdgeEdgeAdapter<T : MediaItemAdapter>(
        val content: T
    ) {
        val startSpace: SpaceAdapter = SpaceAdapter()
        val endSpace: SpaceAdapter = SpaceAdapter()

        val root: ConcatAdapter = ConcatAdapter(
            startSpace,
            content,
            endSpace
        )

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


    }

    protected open class MediaItemAdapter(
        val data: List<MediaInfo.File>,
        val onItemClick: (MediaInfo.File, Int) -> Unit
    ) : RecyclerView.Adapter<MediaItemHolder>() {

        private val loadDelegate = MediaLoadDelegate()

        private var layoutInflater: LayoutInflater? = null

        protected fun getLayoutInflater(parent: ViewGroup): LayoutInflater {
            return layoutInflater ?: LayoutInflater.from(parent.context).also {
                layoutInflater = it
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaItemHolder {
            return MediaItemHolder(
                ItemMediaGridBinding.inflate(getLayoutInflater(parent), parent, false),
                ::onHolderClick
            )
        }

        private fun onHolderClick(position: Int) {
            if (position >= 0 && position < data.size) {
                onItemClick(data[position], position)
            }
        }

        override fun onBindViewHolder(holder: MediaItemHolder, position: Int) {
            holder.bind(data[position])
            loadDelegate.onBind(holder, data[position])
        }

        override fun getItemCount(): Int {
            return data.size
        }

    }

    protected class SpaceAdapter : RecyclerView.Adapter<SpaceHolder>() {

        private val spaceInfo: SpaceInfo = SpaceInfo(0)

        fun setSpaceDp(sizeDp: Int) {
            this.spaceInfo.setDpSize(sizeDp)
            notifyItemChanged(0)
        }

        fun setSpacePx(sizePx: Int) {
            this.spaceInfo.setPxSize(sizePx)
            notifyItemChanged(0)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SpaceHolder {
            return SpaceHolder(Space(parent.context))
        }

        override fun onBindViewHolder(holder: SpaceHolder, position: Int) {
            holder.bind(spaceInfo)
        }

        override fun getItemCount(): Int {
            return 1
        }

    }

    open class SpaceInfo(
        var spaceSizeDp: Int = 0,
        var spaceSizePx: Int = 0
    ) {

        fun setPxSize(px: Int) {
            spaceSizePx = px
            spaceSizeDp = 0
        }

        fun setDpSize(dp: Int) {
            spaceSizeDp = dp
            spaceSizePx = 0
        }

    }

    protected open class SpaceHolder(
        val spaceView: Space
    ) : RecyclerView.ViewHolder(spaceView) {

        fun bind(spaceInfo: SpaceInfo) {
            if (spaceInfo.spaceSizePx > 0) {
                updateLayout {
                    height = spaceInfo.spaceSizePx
                }
            } else if (spaceInfo.spaceSizeDp > 0) {
                updateLayout {
                    height = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        spaceInfo.spaceSizeDp.toFloat(),
                        itemView.context.resources.displayMetrics
                    ).toInt()
                }
            }
        }

        private fun updateLayout(build: ViewGroup.LayoutParams.() -> Unit) {
            val layoutParams = spaceView.layoutParams ?: ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            layoutParams.build()
            spaceView.layoutParams = layoutParams
        }

    }

    protected open class MediaItemHolder(
        val binding: ItemMediaGridBinding,
        val onClick: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        var loadingJob: Job? = null

        init {
            itemView.setOnClickListener {
                onClick(bindingAdapterPosition)
            }
        }

        fun bind(mediaInfo: MediaInfo.File) {
            Glide.with(itemView)
                .load(mediaInfo.uri)
                .into(binding.mediaPreview)
            updateUI(mediaInfo.metadata)
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

    private class MediaLoadDelegate {
        // 缓存已加载或加载中的任务
        private val mediaCache = object : LruCache<String, Deferred<MediaMetadata?>>(200) {
            override fun entryRemoved(
                evicted: Boolean,
                key: String,
                oldValue: Deferred<MediaMetadata?>,
                newValue: Deferred<MediaMetadata?>?
            ) {
                super.entryRemoved(evicted, key, oldValue, newValue)
                if (evicted) {
                    oldValue.cancel()
                }
            }
        }
        private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        fun onBind(holder: MediaItemHolder, info: MediaInfo.File) {
            if (info.metadata != null) {
                holder.loadingJob?.cancel()
                holder.loadingJob = null
                return
            }

            val uri = info.uriString

            // 1. 立即清除旧任务（防止复用导致的显示错位）
            holder.loadingJob?.cancel()

            holder.loadingJob = scope.launch {
                val deferred = synchronized(lock = mediaCache) {
                    val cached = mediaCache[uri]
                    if (cached != null) {
                        cached
                    } else {
                        // 2. 只有缓存没有时，才创建新的 async 任务
                        val newDeferred = async(Dispatchers.IO) {
                            info.loadMetadataSync(holder.itemView.context, cacheOnly = false)
                            info.metadata
                        }
                        mediaCache.put(uri, newDeferred)
                        newDeferred
                    }
                }
                try {
                    // 3. 等待结果（如果已在缓存中，这里会立即返回）
                    val info = deferred.await()
                    holder.updateUI(info)
                } catch (e: CancellationException) {
                    // 正常取消，不做处理
                } catch (e: Exception) {
                }
            }
        }
    }

}