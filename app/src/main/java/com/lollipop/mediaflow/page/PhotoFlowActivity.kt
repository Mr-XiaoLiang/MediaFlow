package com.lollipop.mediaflow.page

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MediaStore
import com.lollipop.mediaflow.data.MediaType
import com.lollipop.mediaflow.data.MediaVisibility
import com.lollipop.mediaflow.page.flow.MediaFlowStoreView
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.ui.BasicFlowActivity
import com.lollipop.mediaflow.ui.MediaGridAdapter

class PhotoFlowActivity : BasicFlowActivity() {

    companion object {

        private const val EXTRA_MEDIA_VISIBILITY = "extra_media_visibility"
        private const val EXTRA_POSITION = "extra_position"

        fun start(context: Context, mediaVisibility: MediaVisibility, position: Int) {
            val intent = Intent(context, PhotoFlowActivity::class.java)
            intent.putExtra(EXTRA_MEDIA_VISIBILITY, mediaVisibility.key)
            intent.putExtra(EXTRA_POSITION, position)
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        private fun getMediaVisibility(intent: Intent): MediaVisibility {
            return intent.getStringExtra(EXTRA_MEDIA_VISIBILITY)?.let {
                MediaVisibility.findByKey(it)
            } ?: MediaVisibility.Public
        }

    }

    private val recyclerView by lazy {
        RecyclerView(this)
    }

    private val mediaData = mutableListOf<MediaInfo.File>()

    private val mediaFlowStoreView by lazy {
        MediaFlowStoreView(::onItemClick)
    }

    private var currentPosition = 0

    private val contentAdapter by lazy {
        MediaGridAdapter.buildLiningEdge(PhotoAdapter(mediaData))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentPosition = intent.getIntExtra(EXTRA_POSITION, 0)
        reloadData()
    }

    private fun findVisibility(): MediaVisibility {
        return getMediaVisibility(intent)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun reloadData() {
        log.i("reloadData")
        val mediaVisibility = findVisibility()
        val gallery = MediaStore.loadGallery(this, mediaVisibility, MediaType.Image)
        gallery.load { gallery, success ->
            mediaData.clear()
            mediaData.addAll(gallery.fileList)
            contentAdapter.content.notifyDataSetChanged()
            mediaFlowStoreView.resetData(mediaData)
            setCurrentItem(currentPosition)
            log.i("reloadData end, isSuccess=$success, mediaCount=${mediaData.size}, index=$currentPosition")
        }
    }

    private fun onItemClick(mediaInfo: MediaInfo.File, position: Int) {
        setCurrentItem(position)
    }

    private fun setCurrentItem(position: Int) {
        recyclerView.scrollToPosition(position)
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
        private val mediaData: List<MediaInfo.File>
    ) : RecyclerView.Adapter<PhotoItemHolder>() {
        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): PhotoItemHolder {
            return PhotoItemHolder.create(parent)
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
        private val imageView: AppCompatImageView
    ) : RecyclerView.ViewHolder(root) {

        companion object {
            fun create(parent: ViewGroup): PhotoItemHolder {
                val root = RatioFrameLayout(parent.context)
                val imageView = AppCompatImageView(parent.context)
                root.addView(
                    imageView,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                return PhotoItemHolder(root, imageView)
            }
        }

        private val log = registerLog()

        init {
            imageView.scaleType = ImageView.ScaleType.FIT_CENTER
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
//            val params = imageView.layoutParams
//            if (params is ConstraintLayout.LayoutParams) {
//                updateLayoutParams(params, width, height)
//                imageView.layoutParams = params
//                return
//            }
//            val layoutParams = ConstraintLayout.LayoutParams(width, height)
//            updateLayoutParams(layoutParams, width, height)
//            imageView.layoutParams = layoutParams
        }

        private fun updateLayoutParams(
            params: ConstraintLayout.LayoutParams,
            width: Int,
            height: Int
        ) {
            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = 0
            params.dimensionRatio = "$width:$height"
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