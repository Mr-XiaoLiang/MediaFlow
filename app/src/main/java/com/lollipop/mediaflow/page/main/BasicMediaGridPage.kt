package com.lollipop.mediaflow.page.main

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
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.GridLayoutManager
import androidx.window.layout.WindowMetricsCalculator
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MediaSort
import com.lollipop.mediaflow.databinding.FragmentMainMediaBinding
import com.lollipop.mediaflow.page.RootUriManagerActivity
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.ui.HomePage
import com.lollipop.mediaflow.ui.IconPopupMenu
import com.lollipop.mediaflow.ui.MediaGridFragment

abstract class BasicMediaGridPage(
    private val page: HomePage
) : MediaGridFragment() {

    companion object {
        private const val KEY_SOURCE_MANAGER = "SourceManager"
    }

    private var layoutManager: GridLayoutManager? = null

    private var binding: FragmentMainMediaBinding? = null

    private val mediaData = ArrayList<MediaInfo.File>()

    private val adapterHolder by lazy {
        buildLiningEdge(MediaItemAdapter(mediaData, ::onItemClick))
    }

    private val sortPopupHolder by lazy {
        IconPopupMenu.hold(::buildSortMenu)
    }

    private val optionPopupHolder by lazy {
        IconPopupMenu.hold(::buildOptionMenu)
    }

    private var callback: Callback? = null

    private var sortType: MediaSort = MediaSort.DateDesc

    private val log = registerLog()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        callback = fetchCallback(context)
    }

    override fun onDetach() {
        super.onDetach()
        callback = null
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
        layoutManager = GridLayoutManager(view.context, 1)
        binding?.apply {
            contentList.layoutManager = layoutManager
            contentList.adapter = adapterHolder.root
            adapterHolder.bindEdgeSpanSizeLookup(contentList)
            refreshLayout.setOnRefreshListener {
                refreshData()
            }
            sortBtn.setOnClickListener {
                sortPopupHolder.show(it)
            }
            menuBtn.setOnClickListener {
                optionPopupHolder.show(it)
            }
        }
        updateSortIcon()
        updateSpanCount()
    }

    override fun onWindowInsetsChanged(insets: Rect) {
        super.onWindowInsetsChanged(insets)
        binding?.apply {
//            contentList.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            refreshLayout.setProgressViewOffset(true, 0, insets.top)
            val actionBarSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                42f,
                root.resources.displayMetrics
            ).toInt()
            adapterHolder.startSpace.setSpacePx(insets.top + actionBarSize)
            adapterHolder.endSpace.setSpacePx(insets.bottom + actionBarSize)
            val minEdge = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                16f,
                root.resources.displayMetrics
            ).toInt()
            startGuideLine.updateLayoutParams<ConstraintLayout.LayoutParams> {
                guideBegin = maxOf(insets.left, minEdge)
            }
            topGuideLine.updateLayoutParams<ConstraintLayout.LayoutParams> {
                guideBegin = maxOf(insets.top, minEdge)
            }
            endGuideLine.updateLayoutParams<ConstraintLayout.LayoutParams> {
                guideEnd = maxOf(insets.right, minEdge)
            }
            bottomGuideLine.updateLayoutParams<ConstraintLayout.LayoutParams> {
                guideEnd = maxOf(insets.bottom, minEdge)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (mediaData.isEmpty()) {
            reloadData()
        }
    }

    private fun reloadData() {
        callback?.onLoad(page = page, sort = sortType, callback = ::onDataLoaded)
    }

    private fun refreshData() {
        callback?.onRefresh(page = page, sort = sortType, callback = ::onDataLoaded)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun onDataLoaded(mediaList: List<MediaInfo.File>) {
        mediaData.clear()
        mediaData.addAll(mediaList)
        adapterHolder.content.notifyDataSetChanged()
        binding?.refreshLayout?.isRefreshing = false
        log.i("onDataLoaded, mediaList.size=${mediaList.size}")
    }

    private fun onItemClick(mediaInfo: MediaInfo.File) {
        callback?.onMediaItemClick(page = page, mediaInfo = mediaInfo)
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
                updateSortIcon()
                reloadData()
                true
            }
    }

    private fun buildOptionMenu(builder: IconPopupMenu.Builder) {
        builder
            .addMenu(
                tag = KEY_SOURCE_MANAGER,
                titleRes = R.string.source_manager,
                iconRes = 0
            )
            .gravity(Gravity.END)
            .offsetDp(0, 8)
            .onClick {
                when (it.tag) {
                    KEY_SOURCE_MANAGER -> {
                        activity?.let { act ->
                            RootUriManagerActivity.start(act, visibility = page.visibility)
                        }
                        true
                    }

                    else -> {
                        false
                    }
                }
            }
    }

    private fun updateSortIcon() {
        binding?.sortIcon?.setImageResource(
            when (sortType) {
                MediaSort.DateDesc -> R.drawable.clock_arrow_down_24
                MediaSort.DateAsc -> R.drawable.clock_arrow_up_24
                MediaSort.NameDesc -> R.drawable.text_arrow_down_24
                MediaSort.NameAsc -> R.drawable.text_arrow_up_24
                MediaSort.Random -> R.drawable.shuffle_24
            }
        )
    }

    private fun updateSpanCount() {
        val act = activity ?: return
        // 获取当前窗口度量值
        val windowMetrics = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(act)
        val widthPx = windowMetrics.bounds.width()

        // 转换 px 为 dp 以适配不同密度
        val density = resources.displayMetrics.density
        val widthDp = widthPx / density

        val columnCount = (widthDp / 80).toInt().coerceAtLeast(1)
        layoutManager?.spanCount = columnCount
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateSpanCount()
    }

    interface Callback {
        fun onMediaItemClick(page: HomePage, mediaInfo: MediaInfo.File)
        fun onLoad(page: HomePage, sort: MediaSort, callback: (List<MediaInfo.File>) -> Unit)
        fun onRefresh(page: HomePage, sort: MediaSort, callback: (List<MediaInfo.File>) -> Unit)
    }

}