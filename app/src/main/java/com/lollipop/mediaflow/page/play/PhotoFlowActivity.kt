package com.lollipop.mediaflow.page.play

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MediaStore
import com.lollipop.mediaflow.data.MediaType
import com.lollipop.mediaflow.page.flow.MediaFlowStoreView
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.MediaPlayLauncher
import com.lollipop.mediaflow.ui.BasicFlowActivity
import com.lollipop.mediaflow.ui.MediaGrid

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
        gallery.load { gallery, success ->
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
        contentAdapter.bindEdgeSpanSizeLookup(contentView)
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
            mediaInfo.loadMetadataSync(imageView.context, false)
            mediaInfo.metadata?.let { metadata ->
                if (metadata.rotation == 90 || metadata.rotation == 270) {
                    updateLayoutParams(metadata.height, metadata.width)
                } else {
                    updateLayoutParams(metadata.width, metadata.height)
                }
            }
            Glide.with(imageView).load(mediaInfo.uri).into(imageView)
        }

        private fun updateLayoutParams(width: Int, height: Int) {
            log.i("updateLayoutParams, width=$width, height=$height")
            root.setRatio(width, height)
        }

    }

    private class RatioFrameLayout(
        context: Context
    ) : FrameLayout(context) {

        private var ratio: Float = 0f

        fun setRatio(width: Int, height: Int) {
            ratio = width.toFloat() / height.toFloat()
            requestLayout()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = (width / ratio).toInt()
            val count = childCount
            val widthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
            val heightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
            for (i in 0 until count) {
                val child = getChildAt(i)
                if (!child.isVisible) {
                    continue
                }
                child.measure(widthSpec, heightSpec)
            }
            setMeasuredDimension(width, height)
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            val width = right - left
            val height = (width / ratio).toInt()
            val count = childCount
            for (i in 0 until count) {
                val child = getChildAt(i)
                if (!child.isVisible) {
                    continue
                }
                child.layout(0, 0, width, height)
            }
        }

    }

}