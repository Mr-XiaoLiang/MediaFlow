package com.lollipop.mediaflow.page.play

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MediaStore
import com.lollipop.mediaflow.data.MediaType
import com.lollipop.mediaflow.data.MetadataLoader
import com.lollipop.mediaflow.page.flow.MediaFlowStoreView
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.MediaPlayLauncher
import com.lollipop.mediaflow.ui.BasicFlowActivity
import com.lollipop.mediaflow.ui.list.MediaGrid
import com.lollipop.mediaflow.ui.view.RatioFrameLayout

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
        MediaGrid.buildLiningEdge(PhotoAdapter(mediaData, ::onFlowItemClick))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaParams.onCreate(this, savedInstanceState)
        reloadData()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun reloadData() {
        log.i("reloadData")
        val mediaVisibility = mediaParams.visibility
        val gallery = MediaStore.loadGallery(this, mediaVisibility, MediaType.Image)
        gallery.loadChoose { gallery, success ->
            mediaData.clear()
            mediaData.addAll(gallery.fileList)
            contentAdapter.content.notifyDataSetChanged()
            mediaFlowStoreView.resetData(mediaData)
            val currentPosition = mediaParams.currentPosition
            setCurrentItem(currentPosition)
            log.i("reloadData end, isSuccess=$success, mediaCount=${mediaData.size}, index=$currentPosition")
        }
    }

    private fun onFlowItemClick(mediaInfo: MediaInfo.File, position: Int) {
        // 每次点击都直接修改装饰元素的显示状态就行了
        changeDecoration(!isDecorationShown)
    }

    private fun onItemClick(position: Int) {
        setCurrentItem(position)
    }

    private fun setCurrentItem(position: Int) {
        recyclerView.scrollToPosition(position + contentAdapter.startSpace.itemCount)
        mediaParams.onSelected(this, position)
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

    override fun onWindowInsetsChanged(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        mediaFlowStoreView.onInsetsChanged(left, top, right, bottom)
        contentAdapter.startSpace.setSpacePx(top)
        contentAdapter.endSpace.setSpacePx(bottom)
    }

    private class PhotoAdapter(
        private val mediaData: List<MediaInfo.File>,
        private val onItemClick: (MediaInfo.File, Int) -> Unit
    ) : RecyclerView.Adapter<PhotoItemHolder>() {
        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): PhotoItemHolder {
            return PhotoItemHolder.create(parent, ::onItemClick)
        }

        private fun onItemClick(position: Int) {
            if (position < 0 || position >= mediaData.size) {
                return
            }
            onItemClick(mediaData[position], position)
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
        private val root: RatioFrameLayout,
        private val imageView: AppCompatImageView,
        private val onItemClick: (Int) -> Unit
    ) : RecyclerView.ViewHolder(root) {

        companion object {
            fun create(parent: ViewGroup, onItemClick: (Int) -> Unit): PhotoItemHolder {
                val root = RatioFrameLayout(parent.context)
                val imageView = AppCompatImageView(parent.context)
                root.addView(
                    imageView,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                return PhotoItemHolder(root, imageView, onItemClick)
            }
        }

        private val log = registerLog()

        init {
            imageView.scaleType = ImageView.ScaleType.FIT_CENTER
            itemView.setOnClickListener {
                onItemViewClick()
            }
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
            }
            Glide.with(imageView).load(mediaInfo.uri).into(imageView)
        }

        private fun updateLayoutParams(width: Int, height: Int) {
            log.i("updateLayoutParams, width=$width, height=$height")
            root.setRatio(width, height, RatioFrameLayout.Mode.WidthFirst)
        }

    }

}