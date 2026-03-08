package com.lollipop.mediaflow.page.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.bumptech.glide.Glide
import com.lollipop.mediaflow.data.MediaChooser
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MediaMetadata
import com.lollipop.mediaflow.data.MediaStore
import com.lollipop.mediaflow.data.MediaType
import com.lollipop.mediaflow.data.MediaVisibility
import com.lollipop.mediaflow.data.MetadataLoader
import com.lollipop.mediaflow.databinding.ActivityArchiveBinding
import com.lollipop.mediaflow.databinding.ItemMediaArchiveBinding
import com.lollipop.mediaflow.tools.MediaPlayLauncher
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.mediaflow.ui.BlurHelper
import com.lollipop.mediaflow.ui.CustomOrientationActivity
import com.lollipop.mediaflow.ui.list.BasicListDelegate.BasicItemAdapter
import com.lollipop.mediaflow.ui.list.MediaStaggered
import com.lollipop.mediaflow.ui.view.RatioFrameLayout
import kotlinx.coroutines.Job


class ArchiveActivity : CustomOrientationActivity() {

    companion object {
        fun start(context: Context, visibility: MediaVisibility, type: MediaType) {
            val archiveUri = Preferences.archiveDirUri.get()
            if (archiveUri.isEmpty() || !MediaChooser.hasWritePermission(context, archiveUri)) {
                ArchiveUriManagerActivity.start(context)
                return
            }
            val intent = MediaPlayLauncher.createIntent(
                context = context,
                visibility = visibility,
                position = 0,
                type,
                target = ArchiveActivity::class.java
            )
            context.startActivity(intent)
        }
    }

    private val binding by lazy {
        ActivityArchiveBinding.inflate(layoutInflater)
    }
    private val mediaData = mutableListOf<MediaInfo.File>()

    private val layoutManager by lazy {
        StaggeredGridLayoutManager(2, RecyclerView.HORIZONTAL)
    }

    private val mediaParams = MediaPlayLauncher.params()

    private val contentAdapter by lazy {
        MediaStaggered.buildLiningEdge(ItemAdapter(data = mediaData))
    }

    private var archiveUri: Uri = Uri.EMPTY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        mediaParams.onCreate(this, savedInstanceState)
        initInsetsListener()
        binding.backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        binding.openButton.setOnClickListener {
        }
        binding.recyclerView.adapter = contentAdapter.root
        binding.recyclerView.layoutManager = layoutManager
        val itemTouchHelper = ItemTouchHelper(ArchiveTouchCallback(::onItemSwiped))
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)
        checkSpanCount()
        updateBlur()
        reloadData()
    }

    private fun onItemSwiped(position: Int) {
        mediaData.removeAt(position)
        contentAdapter.content.notifyItemRemoved(position)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun reloadData() {
        log.i("reloadData")
        val archivePath = Preferences.archiveDirUri.get()
        archiveUri = archivePath.toUri()
        if (archivePath.isEmpty()) {
            finish()
            return
        }
        val mediaVisibility = mediaParams.visibility
        val gallery = MediaStore.loadGallery(this, mediaVisibility, mediaParams.type)
        gallery.loadChoose { gallery, success ->
            val list = gallery.fileList
            onDataChanged(list)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun onDataChanged(list: List<MediaInfo.File>) {
        mediaData.clear()
        mediaData.addAll(list)
        contentAdapter.content.notifyDataSetChanged()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateBlur()
    }

    private fun updateBlur() {
        BlurHelper.bind(
            window,
            binding.blurTarget,
            binding.backButtonBlur,
            binding.openButtonBlur,
        )
    }

    private fun initInsetsListener() {
        initInsetsListener(binding.root)
        bindGuidelineInsets(
            leftGuideline = binding.startGuideLine,
            topGuideline = binding.topGuideLine,
            rightGuideline = binding.endGuideLine,
            bottomGuideline = binding.bottomGuideLine,
        )
    }

    override fun onOrientationChanged(orientation: Orientation) {
        super.onOrientationChanged(orientation)
        checkSpanCount()
    }

    private fun checkSpanCount() {
        MediaStaggered.updateSpanCountHorizontal(layoutManager, this, 220)
    }

    override fun onWindowInsetsChanged(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        val actionSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            36F,
            resources.displayMetrics
        ).toInt()
        binding.recyclerView.setPadding(0, top, 0, 0)
        binding.archiveBar.setPadding(left, 0, right, bottom)
        val isRTL = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val rightEdge = right.coerceAtLeast(minEdge) + actionSize
        val leftEdge = left.coerceAtLeast(minEdge) + actionSize
        if (isRTL) {
            contentAdapter.startSpace.setSpacePx(rightEdge)
            contentAdapter.endSpace.setSpacePx(leftEdge)
        } else {
            contentAdapter.startSpace.setSpacePx(leftEdge)
            contentAdapter.endSpace.setSpacePx(rightEdge)
        }
    }

    private class ItemAdapter(
        data: List<MediaInfo.File>,
    ) : BasicItemAdapter<MediaItemHolder>(data = data) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaItemHolder {
            return MediaItemHolder(
                ItemMediaArchiveBinding.inflate(getLayoutInflater(parent), parent, false),
            )
        }

        override fun onBindViewHolder(holder: MediaItemHolder, position: Int) {
            holder.bind(data[position])
        }

    }

    private class MediaItemHolder(
        val binding: ItemMediaArchiveBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private var loadingJob: Job? = null

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
            val ratioHeight = metadata?.height ?: 1
            var ratioWidth = (metadata?.width ?: 1)
            val maxWidth = (ratioHeight * 1.5F).toInt()
            val minWidth = (ratioHeight * 0.5F).toInt()
            if (ratioWidth > maxWidth) {
                ratioWidth = maxWidth
            }
            if (ratioWidth < minWidth) {
                ratioWidth = minWidth
            }
            binding.ratioLayout.setRatio(ratioWidth, ratioHeight, RatioFrameLayout.Mode.HeightFirst)
        }
    }

    private class ArchiveTouchCallback(
        private val onSwipedCallback: (Int) -> Unit
    ) : ItemTouchHelper.Callback() {
        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            if (viewHolder is MediaItemHolder) {
                val dragFlags = 0
                val swipeFlags = ItemTouchHelper.DOWN
                return makeMovementFlags(dragFlags, swipeFlags)
            }
            return 0
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            return false
        }

        override fun onSwiped(
            viewHolder: RecyclerView.ViewHolder,
            direction: Int
        ) {
            onSwipedCallback(viewHolder.bindingAdapterPosition)
        }

    }

}