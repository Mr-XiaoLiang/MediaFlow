package com.lollipop.mediaflow.page.main

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.lollipop.common.tools.LLog.Companion.registerLog
import com.lollipop.common.tools.postUI
import com.lollipop.common.ui.page.InsetsFragment
import com.lollipop.common.ui.page.fetchCallback
import com.lollipop.common.ui.view.IconPopupMenu
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.data.local.MediaInfo
import com.lollipop.mediaflow.data.local.MediaSort
import com.lollipop.mediaflow.databinding.FragmentMainMediaBinding
import com.lollipop.mediaflow.databinding.ItemHomeSloganBinding
import com.lollipop.mediaflow.page.settings.RootUriManagerActivity
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.mediaflow.ui.FastScrollerView
import com.lollipop.mediaflow.ui.HomePage
import com.lollipop.mediaflow.ui.list.MediaStaggered
import kotlin.math.abs

abstract class BasicMediaGridPage(
    private val page: HomePage
) : InsetsFragment() {

    private var binding: FragmentMainMediaBinding? = null

    private val mediaData = ArrayList<MediaInfo.File>()

    private val gridAdapterDelegate by lazy {
        MediaStaggered.buildDelegate(
            contentAdapter = MediaStaggered.ItemAdapter(
                data = mediaData,
                onItemClick = ::onItemClick
            ),
            headerAdapter = SloganAdapter()
        )
    }

    private val sortPopupHolder by lazy {
        IconPopupMenu.hold(::buildSortMenu)
    }

    private var callback: Callback? = null

    private var sortType: MediaSort
        get() {
            return page.sortType
        }
        set(value) {
            page.sortType = value
        }

    private val log = registerLog()

    private var loadCount = 0

    private var dataVersion = -1L

    private var isLabelsVisible = false

    private val fragmentHolder by lazy {
        FragmentHolderImpl(
            page = page,
            sortMenuHolder = sortPopupHolder,
            onDataChangedCallback = ::reloadData,
            dataVersionCallback = ::checkDataVersion,
            selectToCallback = ::callSelectTo,
            scrollToTopCallback = ::scrollToTop
        )
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        callback = fetchCallback(context)
    }

    override fun onDetach() {
        super.onDetach()
        callback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isLabelsVisible = Preferences.isDisplayLabelInList.get()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentMainMediaBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.let {
            onViewCreated(it)
        }
    }

    private fun onViewCreated(b: FragmentMainMediaBinding) {
        gridAdapterDelegate.bind(b.contentList, activity)
        b.refreshLayout.setOnRefreshListener {
            refreshData()
        }
        b.addSourceButton.setOnClickListener {
            RootUriManagerActivity.start(it.context, page.visibility)
        }
        FastScrollDelegate.bind(b.contentList, b.fastScrollerView)
    }

    override fun onWindowInsetsChanged(insets: Rect) {
        super.onWindowInsetsChanged(insets)
        binding?.apply {
            refreshLayout.setProgressViewOffset(true, 0, insets.top)
            val optionBarSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                72F,
                root.resources.displayMetrics
            ).toInt()
            gridAdapterDelegate.onInsetsChanged(
                insets.top,
                insets.bottom + optionBarSize
            )
            val dp4 = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                4f,
                root.resources.displayMetrics
            ).toInt()
            contentList.setPadding(insets.left + dp4, 0, insets.right + dp4, 0)
        }
    }

    protected open fun callSelectTo(position: Int) {
        if (mediaData.size > position && position >= 0) {
            binding?.contentList?.scrollToPosition(position)
        }
    }

    protected open fun scrollToTop() {
        binding?.contentList?.scrollToPosition(0)
    }

    override fun onResume() {
        super.onResume()
        callback?.onPageResume(fragmentHolder)
        gridAdapterDelegate.header.notifyItemChanged(0)
        binding?.let {
            it.fastScrollerView.isVisible = Preferences.isFastScrollerEnable.get()
        }
        val newLabelVisible = Preferences.isDisplayLabelInList.get()
        if (isLabelsVisible != newLabelVisible) {
            isLabelsVisible = newLabelVisible
            gridAdapterDelegate.notifyContentDataSetChanged()
        }
    }

    private fun reloadData() {
        callback?.onLoad(page = page, sort = sortType, callback = ::onDataLoaded)
    }

    private fun checkDataVersion(version: Long) {
        if (dataVersion != version) {
            reloadData()
        }
    }

    private fun refreshData() {
        loadCount++
        binding?.refreshLayout?.isRefreshing = true
        callback?.onRefresh(page = page, sort = sortType, callback = ::onDataLoaded)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun onDataLoaded(version: Long, mediaList: List<MediaInfo.File>) {
        postUI {
            dataVersion = version
            mediaData.clear()
            mediaData.addAll(mediaList)
            binding?.emptyMediaView?.isVisible = mediaList.isEmpty()
            gridAdapterDelegate.notifyContentDataSetChanged()
            binding?.refreshLayout?.isRefreshing = false
            log.i("onDataLoaded, mediaList.size=${mediaList.size}")
            if (mediaList.isEmpty() && loadCount < 1) {
                // 如果为空，并且没有自动刷新过，那么自动刷新一下
                refreshData()
            }
        }
    }

    private fun onItemClick(position: Int) {
        callback?.onMediaItemClick(page = page, position = position)
    }

    private fun buildSortMenu(builder: IconPopupMenu.Builder) {
        builder.addMenu(
            tag = MediaSort.DateDesc.key,
            titleRes = R.string.sort_date_desc,
            iconRes = R.drawable.clock_arrow_down_24
        )
            .addMenu(
                tag = MediaSort.DateAsc.key,
                titleRes = R.string.sort_date_asc,
                iconRes = R.drawable.clock_arrow_up_24
            )
            .addMenu(
                tag = MediaSort.NameDesc.key,
                titleRes = R.string.sort_text_desc,
                iconRes = R.drawable.text_arrow_down_24
            )
            .addMenu(
                tag = MediaSort.NameAsc.key,
                titleRes = R.string.sort_text_asc,
                iconRes = R.drawable.text_arrow_up_24
            )
            .addMenu(
                tag = MediaSort.Random.key,
                titleRes = R.string.sort_random,
                iconRes = R.drawable.shuffle_24
            )
            .gravity(Gravity.END)
            .offsetDp(0, 8)
            .onClick {
                sortType = MediaSort.findByKey(it.tag) ?: MediaSort.DateDesc
                reloadData()
                true
            }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        gridAdapterDelegate.updateSpanCount(activity)
    }

    private class FastScrollDelegate(
        private val recyclerView: RecyclerView,
        private val fastScrollerView: FastScrollerView
    ) : RecyclerView.OnScrollListener(), FastScrollerView.OnDragListener {

        companion object {
            fun bind(recyclerView: RecyclerView, fastScrollerView: FastScrollerView) {
                FastScrollDelegate(recyclerView, fastScrollerView)
            }
        }

        private var isDragging = false
        private var staggeredGridPositionArray: IntArray? = null
        private var lastTouchTime = 0L
        private var lastFistPosition = 0

        private var animator = ValueAnimator().also {
            it.interpolator = LinearInterpolator()
            it.addUpdateListener { animator ->
                val value = animator.animatedValue
                if (value is Float) {
                    fastScrollerView.updateProgress(value)
                }
            }
        }

        private var animationDuration = 1000L

        init {
            recyclerView.addOnScrollListener(this)
            fastScrollerView.onDragListener = this
        }

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            if (isDragging) {
                return
            }
            val layoutManager = recyclerView.layoutManager
            val itemCount = recyclerView.adapter?.itemCount ?: 0
            when (layoutManager) {
                is GridLayoutManager -> {
                    val firstPosition = layoutManager.findFirstVisibleItemPosition()
                    val lastPosition = layoutManager.findLastVisibleItemPosition()
                    onScrolled(itemCount = itemCount, first = firstPosition, last = lastPosition)
                }

                is StaggeredGridLayoutManager -> {
                    val array = staggeredGridPositionArray
                    val positionArray = if (array == null) {
                        IntArray(layoutManager.spanCount)
                    } else if (array.size != layoutManager.spanCount) {
                        IntArray(layoutManager.spanCount)
                    } else {
                        array
                    }
                    staggeredGridPositionArray = positionArray
                    layoutManager.findFirstVisibleItemPositions(positionArray)
                    val firstPosition = positionArray.minOf { it }
                    layoutManager.findLastVisibleItemPositions(positionArray)
                    val lastPosition = positionArray.maxOf { it }
                    onScrolled(itemCount = itemCount, first = firstPosition, last = lastPosition)
                }

                is LinearLayoutManager -> {
                    val firstPosition = layoutManager.findFirstVisibleItemPosition()
                    val lastPosition = layoutManager.findLastVisibleItemPosition()
                    onScrolled(itemCount = itemCount, first = firstPosition, last = lastPosition)
                }

                else -> {
                    // 什么也不会
                }
            }
        }

        private fun calculateProgress(itemCount: Int, first: Int, last: Int): Float {
            var progress = 0F
            val content = last - first
            if (content >= 0) {
                val offsetCount = itemCount - content
                if (offsetCount > 0 && first >= 0) {
                    progress = first.toFloat() / offsetCount
                }
            }
            return progress
        }

        private fun onScrolled(itemCount: Int, first: Int, last: Int) {
            if (lastFistPosition == first) {
                if (!animator.isRunning) {
                    fastScrollerView.updateProgress(calculateProgress(itemCount, first, last))
                }
                return
            }
            animator.cancel()
            val currentProgress = fastScrollerView.progress
            val progress = calculateProgress(itemCount, first, last)
            animator.setFloatValues(currentProgress, progress)
            val duration = (animationDuration * (abs(progress - currentProgress))).toLong()
            animator.duration = duration
            animator.start()
            lastFistPosition = first
        }

        override fun onDragStart() {
            isDragging = true
        }

        override fun onProgressChange(progress: Float, fromUser: Boolean) {
            if (!fromUser) {
                return
            }
            val last = lastTouchTime
            val now = System.currentTimeMillis()
            if (now - last > 50) {
                lastTouchTime = now
                updatePosition(progress)
            }
        }

        override fun onDragEnd() {
            isDragging = false
            updatePosition(fastScrollerView.progress)
        }

        private fun updatePosition(progress: Float) {
            val itemCount = recyclerView.adapter?.itemCount ?: return
            if (itemCount < 2) {
                return
            }
            var position = (itemCount * progress).toInt()
            if (position < 0) {
                position = 0
            }
            if (position >= itemCount) {
                position = itemCount - 1
            }
            recyclerView.smoothScrollToPosition(position)
        }

    }

    private class SloganAdapter : RecyclerView.Adapter<SloganHolder>() {

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): SloganHolder {
            return SloganHolder(
                ItemHomeSloganBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }

        override fun onBindViewHolder(
            holder: SloganHolder,
            position: Int
        ) {
            holder.onBind()
        }

        override fun onViewAttachedToWindow(holder: SloganHolder) {
            super.onViewAttachedToWindow(holder)
            val itemView = holder.itemView
            val layoutParams = itemView.layoutParams
            if (layoutParams is StaggeredGridLayoutManager.LayoutParams) {
                layoutParams.isFullSpan = true
            }
        }

        override fun getItemCount(): Int {
            return 1
        }

    }

    private class SloganHolder(
        private val binding: ItemHomeSloganBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun onBind() {
            val isShow = Preferences.isSloganEnable.get()
            if (!isShow) {
                // 不显示就自己占位就行了，不显示内容
                binding.sloganIcon.isInvisible = true
                binding.customSloganView.isVisible = false
                return
            }
            val customValue = Preferences.customSloganValue.get()
            if (customValue.isNotEmpty()) {
                binding.sloganIcon.isVisible = false
                binding.customSloganView.isVisible = true
                binding.customSloganView.text = customValue
            } else {
                binding.sloganIcon.isVisible = true
                binding.customSloganView.isVisible = false
            }
        }

    }

    interface Callback {
        fun onMediaItemClick(page: HomePage, position: Int)
        fun onLoad(
            page: HomePage,
            sort: MediaSort,
            callback: (version: Long, List<MediaInfo.File>) -> Unit
        )

        fun onRefresh(
            page: HomePage,
            sort: MediaSort,
            callback: (version: Long, List<MediaInfo.File>) -> Unit
        )

        fun onPageResume(holder: FragmentHolder)
    }

    interface FragmentHolder {
        val page: HomePage
        fun onSortClick(clickedView: View)
        fun onDataChanged()
        fun checkDataVersion(version: Long)

        fun selectTo(position: Int)

        fun onTopClick(clickedView: View)
    }

    private class FragmentHolderImpl(
        override val page: HomePage,
        private val sortMenuHolder: IconPopupMenu.MenuHolder,
        private val onDataChangedCallback: () -> Unit,
        private val dataVersionCallback: (version: Long) -> Unit,
        private val selectToCallback: (position: Int) -> Unit,
        private val scrollToTopCallback: () -> Unit
    ) : FragmentHolder {

        override fun onSortClick(clickedView: View) {
            sortMenuHolder.show(clickedView)
        }

        override fun onDataChanged() {
            onDataChangedCallback()
        }

        override fun checkDataVersion(version: Long) {
            dataVersionCallback(version)
        }

        override fun selectTo(position: Int) {
            selectToCallback(position)
        }

        override fun onTopClick(clickedView: View) {
            scrollToTopCallback()
        }
    }

}