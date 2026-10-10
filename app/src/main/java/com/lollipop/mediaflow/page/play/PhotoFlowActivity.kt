package com.lollipop.mediaflow.page.play

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lollipop.common.tools.LLog.Companion.registerLog
import com.lollipop.common.ui.page.PageOrientation
import com.lollipop.common.ui.view.RatioFrameLayout
import com.lollipop.mediaflow.data.MediaSource
import com.lollipop.mediaflow.data.SourceLoader
import com.lollipop.mediaflow.data.local.ArchiveQuick
import com.lollipop.mediaflow.data.local.LocalState
import com.lollipop.mediaflow.data.local.MediaInfo
import com.lollipop.mediaflow.data.local.MediaType
import com.lollipop.mediaflow.data.local.MetadataLoader
import com.lollipop.mediaflow.databinding.ItemPhotoFlowBinding
import com.lollipop.mediaflow.page.flow.MediaFlowStoreView
import com.lollipop.mediaflow.tools.ArchiveHelper
import com.lollipop.mediaflow.tools.MediaPlayLauncher
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.mediaflow.ui.BasicFlowActivity
import com.lollipop.mediaflow.ui.CoverLoader
import com.lollipop.mediaflow.ui.PhotoFullPreviewDelegate
import com.lollipop.mediaflow.ui.list.MediaGrid
import kotlinx.coroutines.launch

/**
 * ⚠️ 旧 View 表现层（**待删除**）。首页图片入口已迁至 `PhotoFlowComposeActivity`
 * （见 `MediaPlayLauncher` 与 `AndroidManifest.xml`）；本类仅保留代码供人工验证对照，
 * 验证通过后随旧表现层一并删除。
 */
class PhotoFlowActivity : BasicFlowActivity() {

    private val recyclerView by lazy {
        RecyclerView(this)
    }

    private val mediaData = mutableListOf<MediaInfo.File>()

    private val mediaFlowStoreView by lazy {
        MediaFlowStoreView(::onItemClick)
    }

    private val mediaParams = MediaPlayLauncher.params()

    private val contentAdapter by lazy {
        MediaGrid.buildLiningEdge(PhotoAdapter(mediaData, ::onFlowItemClick, ::onArchiveClick))
    }

    private val previewDelegate by lazy {
        PhotoFullPreviewDelegate(this, ::onPreviewClose)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaParams.onCreate(this, savedInstanceState)
        setAppearanceLightStatusBars(false)
        reloadData()
        previewDelegate.onCreate()
    }


    private fun reloadData() {
        log.i("reloadData")
        val currentPosition = mediaParams.currentPosition
        val state = LocalState.of(mediaParams.visibility, MediaType.Image)
        val source = MediaSource.of(mediaParams.visibility, MediaType.Image)
        lifecycleScope.launch {
            // fill 只从缓存投影（快），不触发扫盘；Random 排序由 Gallery 层短路复用上次投影，避免重复乱序
            SourceLoader.Local.fill(this@PhotoFlowActivity, state)
            onMediaLoaded(source.local.filterIsInstance<MediaInfo.File>(), currentPosition)
            log.i("reloadData end, mediaCount=${source.local.size}, index=$currentPosition")
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun onMediaLoaded(mediaList: List<MediaInfo.File>, currentPosition: Int) {
        mediaData.clear()
        mediaData.addAll(mediaList)
        updateSideMediaData(mediaData)
        contentAdapter.content.notifyDataSetChanged()
        mediaFlowStoreView.resetData(mediaData)

        setCurrentItem(currentPosition)
    }

    private fun onPreviewClose() {
        changeDecoration(true)
    }

    private fun onFlowItemClick(mediaInfo: MediaInfo.File, position: Int) {
        // 每次点击都直接修改装饰元素的显示状态就行了
        changeDecoration(false)
        recyclerView.findViewHolderForAdapterPosition(toGlobalPosition(position))?.let { holder ->
            previewDelegate.show(mediaInfo.uri, holder.itemView)
        }
    }

    private fun onArchiveClick(mediaInfo: MediaInfo.File, position: Int) {
        // 防御：与视频页同类问题——holder 解绑 / 数据变更后 position 可能已越界。
        if (position < 0 || position >= mediaData.size) {
            return
        }
        lifecycleScope.launch {
            ArchiveHelper.remove(
                this@PhotoFlowActivity,
                mediaInfo,
                ArchiveQuick.Other,
                mediaParams.visibility
            ) {
                if (position < mediaData.size) {
                    mediaData.removeAt(position)
                    removeSideAt(position)
                    contentAdapter.content.notifyItemRemoved(position)
                }
            }
        }
    }

    private fun onItemClick(position: Int) {
        setCurrentItem(position)
    }

    override fun onSideItemClick(mediaInfo: MediaInfo.File, position: Int) {
        setCurrentItem(position)
    }

    private fun setCurrentItem(position: Int) {
        recyclerView.scrollToPosition(toGlobalPosition(position))
        mediaParams.onSelected(this, position)
    }

    private fun toGlobalPosition(position: Int): Int {
        return position + contentAdapter.startSpace.itemCount
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mediaParams.onSaveInstanceState(this, outState)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mediaFlowStoreView.updateSpanCount(this)
    }

    override fun onDrawerChanged(isOpen: Boolean) {
    }

    override fun createDrawerPanel(): View {
        return mediaFlowStoreView.getView(this)
    }

    override fun createContentPanel(): View {
        return recyclerView.also {
            buildContentView(it)
        }
    }

    private fun buildContentView(contentView: RecyclerView) {
        contentView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        contentView.adapter = contentAdapter.root
        MediaGrid.bindEdgeSpanSizeLookup(contentView)
        contentView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    mediaParams.onSelected(this@PhotoFlowActivity, findFirstPosition(recyclerView))
                }
            }
        })
    }

    private fun findFirstPosition(recyclerView: RecyclerView): Int {
        recyclerView.layoutManager?.let { lm ->
            if (lm is LinearLayoutManager) {
                return lm.findFirstCompletelyVisibleItemPosition()
            }
        }
        return 0
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onOrientationChanged(orientation: PageOrientation) {
        super.onOrientationChanged(orientation)
        contentAdapter.content.notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onSidePanelUpdate(isShown: Boolean) {
        super.onSidePanelUpdate(isShown)
        contentAdapter.content.notifyDataSetChanged()
    }

    override fun onWindowInsetsChanged(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        super.onWindowInsetsChanged(left, top, right, bottom)
        mediaFlowStoreView.onInsetsChanged(left, top, right, bottom)
        contentAdapter.startSpace.setSpacePx(top)
        contentAdapter.endSpace.setSpacePx(bottom)
    }

    private class PhotoAdapter(
        private val mediaData: List<MediaInfo.File>,
        private val onItemClick: (MediaInfo.File, Int) -> Unit,
        private val onArchiveClick: (MediaInfo.File, Int) -> Unit
    ) : RecyclerView.Adapter<PhotoItemHolder>() {

        private var layoutInflaterImpl: LayoutInflater? = null

        private fun getLayoutInflater(context: Context): LayoutInflater {
            return layoutInflaterImpl ?: LayoutInflater.from(context).also {
                layoutInflaterImpl = it
            }
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): PhotoItemHolder {
            return PhotoItemHolder(
                ItemPhotoFlowBinding.inflate(getLayoutInflater(parent.context)),
                ::onItemClick,
                ::onArchiveClick
            )
        }

        private fun onItemClick(position: Int) {
            if (position < 0 || position >= mediaData.size) {
                return
            }
            onItemClick(mediaData[position], position)
        }

        private fun onArchiveClick(position: Int) {
            if (position < 0 || position >= mediaData.size) {
                return
            }
            onArchiveClick(mediaData[position], position)
        }

        override fun onBindViewHolder(
            holder: PhotoItemHolder,
            position: Int
        ) {
            holder.bind(mediaData[position])
        }

        override fun getItemCount(): Int {
            return mediaData.size
        }

    }

    private class PhotoItemHolder(
        private val binding: ItemPhotoFlowBinding,
        private val onItemClick: (Int) -> Unit,
        private val onArchiveClick: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private val log = registerLog()

        init {
            binding.photoView.scaleType = ImageView.ScaleType.FIT_CENTER
            binding.photoView.setOnClickListener {
                onItemViewClick()
            }
            binding.archiveButton.setOnClickListener {
                onArchiveClick(bindingAdapterPosition)
            }
            binding.archiveButton.isVisible = Preferences.isQuickArchiveEnable.get()
        }

        private fun onItemViewClick() {
            onItemClick(bindingAdapterPosition)
        }

        fun bind(mediaInfo: MediaInfo.File) {
            MetadataLoader.load(itemView.context, mediaInfo) { metadata ->
                log.i("bind: ${metadata?.width} * ${metadata?.height}, ${metadata?.rotation}")
                if (metadata != null) {
                    if (metadata.needRotate) {
                        updateLayoutParams(metadata.height, metadata.width)
                    } else {
                        updateLayoutParams(metadata.width, metadata.height)
                    }
                } else {
                    updateLayoutParams(1, 1)
                }
                binding.photoView.post {
                    CoverLoader.load(binding.photoView, mediaInfo)
                }
            }
        }

        private fun updateLayoutParams(width: Int, height: Int) {
            log.i("updateLayoutParams, width=$width, height=$height")
            binding.root.setRatio(
                width.coerceAtLeast(1),
                height.coerceAtLeast(1),
                RatioFrameLayout.Mode.WidthFirst
            )
        }

    }

}