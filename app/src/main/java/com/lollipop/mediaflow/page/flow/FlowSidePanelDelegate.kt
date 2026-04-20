package com.lollipop.mediaflow.page.flow

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Space
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.carousel.CarouselLayoutManager
import com.google.android.material.carousel.HeroCarouselStrategy
import com.google.android.material.carousel.MaskableFrameLayout
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.databinding.ItemMediaGalleryBinding

class FlowSidePanelDelegate(
    val lifecycle: Lifecycle,
    val recyclerView: RecyclerView,
    val onMediaClick: (mediaInfo: MediaInfo.File, position: Int) -> Unit
) {

    private val mediaData = ArrayList<MediaInfo.File>()

    private val topSpaceAdapter by lazy {
        SpaceAdapter()
    }

    private val bottomSpaceAdapter by lazy {
        SpaceAdapter()
    }

    private val selectionTracker by lazy {
        SelectionTracker(
            keyToPosition = ::findGalleryItemPosition,
            positionToKey = ::findGalleryItemKey,
            onSelectChanged = ::onSelectChanged
        )
    }

    private val carouselLayoutManager by lazy {
        CarouselLayoutManager(HeroCarouselStrategy())
    }

    private val galleryItemAdapter by lazy {
        GalleryItemAdapter(
            mediaData = mediaData,
            selectionTracker = selectionTracker,
            onClick = onMediaClick
        )
    }

    fun onCreate() {
        recyclerView.adapter = ConcatAdapter(
            topSpaceAdapter,
            galleryItemAdapter,
            bottomSpaceAdapter
        )
        recyclerView.setLayoutManager(carouselLayoutManager)
        carouselLayoutManager.orientation = RecyclerView.VERTICAL
    }

    fun onInsetsChanged(left: Int, top: Int, right: Int, bottom: Int) {
        topSpaceAdapter.updateSpaceSize(top)
        bottomSpaceAdapter.updateSpaceSize(bottom)
    }

    fun removeAt(position: Int): MediaInfo.File? {
        if (position < 0 || position >= mediaData.size) {
            return null
        }
        val removedFile = mediaData[position]
        mediaData.removeAt(position)
        galleryItemAdapter.notifyItemRemoved(position)
        return removedFile
    }

    fun onSelected(mediaInfo: MediaInfo?, position: Int) {
        selectionTracker.select(mediaInfo?.uriString ?: "")
        if (position >= 0 && position < mediaData.size) {
            if (lifecycle.currentState == Lifecycle.State.RESUMED) {
                recyclerView.smoothScrollToPosition(position)
            } else {
                recyclerView.scrollToPosition(position)
            }
        }
    }

    fun onDataChanged(list: List<MediaInfo.File>) {
        mediaData.clear()
        mediaData.addAll(list)
        galleryItemAdapter.notifyDataSetChanged()
    }

    fun currentPosition(): Int {
        return findGalleryItemPosition(selectionTracker.selectedKey)
    }

    private fun onSelectChanged(old: Int, new: Int) {
        if (old >= 0 && old < mediaData.size) {
            galleryItemAdapter.notifyItemChanged(old)
        }
        if (new >= 0 && new < mediaData.size) {
            galleryItemAdapter.notifyItemChanged(new)
        }
    }

    private fun findGalleryItemKey(position: Int): String {
        if (position < 0 || position >= mediaData.size) {
            return ""
        }
        return mediaData[position].uriString
    }

    private fun findGalleryItemPosition(key: String): Int {
        for (i in mediaData.indices) {
            val media = mediaData[i]
            if (media.uriString == key) {
                return i
            }
        }
        return -1
    }


    private class GalleryItemAdapter(
        private val mediaData: List<MediaInfo.File>,
        private val selectionTracker: SelectionTracker,
        private val onClick: (MediaInfo.File, Int) -> Unit
    ) : RecyclerView.Adapter<GalleryItemHolder>() {

        private var layoutInflater: LayoutInflater? = null
        private fun getLayoutInflater(parent: ViewGroup): LayoutInflater {
            return layoutInflater ?: LayoutInflater.from(parent.context).also {
                layoutInflater = it
            }
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): GalleryItemHolder {
            return GalleryItemHolder(
                ItemMediaGalleryBinding.inflate(getLayoutInflater(parent), parent, false),
                ::onItemClick
            )
        }

        private fun onItemClick(position: Int) {
            if (position < 0 || position >= mediaData.size) {
                return
            }
            onClick(mediaData[position], position)
        }

        override fun onBindViewHolder(
            holder: GalleryItemHolder,
            position: Int
        ) {
            holder.bind(mediaData[position], selectionTracker.isSelected(position))
        }

        override fun getItemCount(): Int {
            return mediaData.size
        }

    }

    private class GalleryItemHolder(
        private val binding: ItemMediaGalleryBinding,
        private val onClick: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.imageView.setOnClickListener {
                onIconClick()
            }
        }

        private fun onIconClick() {
            onClick(bindingAdapterPosition)
        }

        fun bind(mediaInfo: MediaInfo, isSelected: Boolean) {
            Glide.with(binding.imageView).load(mediaInfo.uri).into(binding.imageView)
            binding.flagView.isVisible = isSelected
        }

    }

    private class SelectionTracker(
        val positionToKey: (Int) -> String,
        val keyToPosition: (String) -> Int,
        val onSelectChanged: (Int, Int) -> Unit
    ) {

        var selectedKey = ""
            private set

        fun select(key: String) {
            val oldPosition = keyToPosition(selectedKey)
            selectedKey = key
            val newPosition = keyToPosition(selectedKey)
            onSelectChanged(oldPosition, newPosition)
        }

        fun isSelected(position: Int): Boolean {
            return isSelected(positionToKey(position))
        }

        fun isSelected(key: String): Boolean {
            return key == selectedKey
        }

    }

    private class SpaceAdapter : RecyclerView.Adapter<SpaceHolder>() {

        private var spaceSize = 0

        fun updateSpaceSize(size: Int) {
            if (spaceSize != size) {
                spaceSize = size
                notifyItemChanged(0)
            }
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): SpaceHolder {
            return SpaceHolder.create(parent.context)
        }

        override fun onBindViewHolder(
            holder: SpaceHolder,
            position: Int
        ) {
            holder.bind(spaceSize)
        }

        override fun getItemCount(): Int {
            return 1
        }

    }

    private class SpaceHolder(
        val spaceView: View,
        root: View
    ) : RecyclerView.ViewHolder(root) {

        companion object {
            fun create(context: Context): SpaceHolder {
                val spaceView = Space(context)
                val frameLayout = MaskableFrameLayout(context)
                frameLayout.addView(spaceView, ViewGroup.LayoutParams.MATCH_PARENT, 0)
                frameLayout.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                return SpaceHolder(spaceView, frameLayout)
            }
        }

        fun bind(size: Int) {
            spaceView.updateLayoutParams {
                height = size
            }
        }

    }

}