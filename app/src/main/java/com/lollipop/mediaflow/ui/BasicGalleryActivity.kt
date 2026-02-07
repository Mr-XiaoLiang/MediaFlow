package com.lollipop.mediaflow.ui

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.TransitionManager
import com.bumptech.glide.Glide
import com.google.android.material.carousel.CarouselLayoutManager
import com.google.android.material.carousel.HeroCarouselStrategy
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.databinding.ActivityGalleryBinding
import com.lollipop.mediaflow.databinding.ItemMediaGalleryBinding

abstract class BasicGalleryActivity : CustomOrientationActivity() {

    private val basicBinding by lazy {
        ActivityGalleryBinding.inflate(layoutInflater)
    }
    private val mediaData = ArrayList<MediaInfo>()
    private val selectionTracker by lazy {
        SelectionTracker(
            keyToPosition = ::findGalleryItemPosition,
            positionToKey = ::findGalleryItemKey,
            onSelectChanged = ::onSelectChanged
        )
    }
    private val galleryItemAdapter by lazy {
        GalleryItemAdapter(
            mediaData = mediaData,
            selectionTracker = selectionTracker,
            onClick = ::onMediaClick
        )
    }

    private val carouselLayoutManager by lazy {
        CarouselLayoutManager(HeroCarouselStrategy())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(basicBinding.root)
        initInsetsListener()
        basicBinding.contentContainer.addView(
            createContentPanel(),
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        basicBinding.backBtn.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        basicBinding.focusBtn.setOnClickListener {
            scrollToCurrent()
        }
        initGallery()
        updateBlur()
    }

    private fun initInsetsListener() {
        initInsetsListener(basicBinding.root)
        bindGuidelineInsets(
            leftGuideline = basicBinding.startGuideLine,
            topGuideline = basicBinding.topGuideLine,
            rightGuideline = basicBinding.endGuideLine,
            bottomGuideline = basicBinding.bottomGuideLine,
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateBlur()
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

    private fun scrollToCurrent() {
        val position = findGalleryItemPosition(selectionTracker.selectedKey)
        if (position < 0 || position >= mediaData.size) {
            return
        }
        basicBinding.galleryList.scrollToPosition(position)
    }

    private fun onSelectChanged(old: Int, new: Int) {
        if (old >= 0 && old < mediaData.size) {
            galleryItemAdapter.notifyItemChanged(old)
        }
        if (new >= 0 && new < mediaData.size) {
            galleryItemAdapter.notifyItemChanged(new)
        }
    }

    private fun initGallery() {
        basicBinding.galleryList.adapter = galleryItemAdapter
        basicBinding.galleryList.setLayoutManager(carouselLayoutManager)
//        CarouselSnapHelper().attachToRecyclerView(basicBinding.galleryList)
    }

    override fun onOrientationChanged(orientation: Orientation) {
        super.onOrientationChanged(orientation)
        val constraintLayout = basicBinding.root
        val constraintSet = ConstraintSet()
        if (orientation == Orientation.LANDSCAPE) {
            // 加载横屏的约束定义
            constraintSet.clone(this, R.layout.activity_gallery_land)
            basicBinding.galleryList.updateLayoutParams {
                height = ViewGroup.LayoutParams.MATCH_PARENT
                width = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    64F,
                    resources.displayMetrics
                ).toInt()
            }
            carouselLayoutManager.orientation = RecyclerView.VERTICAL
        } else {
            // 加载竖屏的约束定义
            constraintSet.clone(this, R.layout.activity_gallery)
            basicBinding.galleryList.updateLayoutParams {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    64F,
                    resources.displayMetrics
                ).toInt()
            }
            carouselLayoutManager.orientation = RecyclerView.HORIZONTAL
        }

        // 关键：开启过渡动画，让播放器从“上面”丝滑地移动到“左边”
        TransitionManager.beginDelayedTransition(constraintLayout)
        constraintSet.applyTo(constraintLayout)

    }

    override fun onWindowInsetsChanged(left: Int, top: Int, right: Int, bottom: Int) {
        when (currentOrientation) {
            Orientation.PORTRAIT -> {
                basicBinding.galleryPanel.setPadding(0, 0, 0, bottom)
            }

            Orientation.LANDSCAPE -> {
                basicBinding.galleryPanel.setPadding(0, 0, right, 0)
            }
        }
    }

    private fun updateBlur() {
        BlueHelper.bind(
            window,
            basicBinding.blurTarget,
            basicBinding.backBtnBlur,
            basicBinding.focusBtnBlur
        )
    }

    protected fun onSelected(mediaInfo: MediaInfo?) {
        selectionTracker.select(mediaInfo?.uriString ?: "")
    }

    @SuppressLint("NotifyDataSetChanged")
    protected fun onGalleryDataChanged(list: List<MediaInfo>) {
        mediaData.clear()
        mediaData.addAll(list)
        galleryItemAdapter.notifyDataSetChanged()
    }

    protected abstract fun createContentPanel(): View

    protected abstract fun onMediaClick(mediaInfo: MediaInfo, position: Int)

    protected class GalleryItemAdapter(
        private val mediaData: List<MediaInfo>,
        private val selectionTracker: SelectionTracker,
        private val onClick: (MediaInfo, Int) -> Unit
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

    protected class GalleryItemHolder(
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

    protected class SelectionTracker(
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

}