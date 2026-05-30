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
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MediaSort
import com.lollipop.mediaflow.databinding.FragmentMainMediaBinding
import com.lollipop.common.tools.LLog.Companion.registerLog
import com.lollipop.common.ui.page.fetchCallback
import com.lollipop.common.tools.postUI
import com.lollipop.mediaflow.ui.HomePage
import com.lollipop.common.ui.page.InsetsFragment
import com.lollipop.common.ui.view.IconPopupMenu
import com.lollipop.mediaflow.ui.list.MediaStaggered

abstract class BasicMediaGridPage(
    private val page: HomePage
) : InsetsFragment() {

    private var binding: FragmentMainMediaBinding? = null

    private val mediaData = ArrayList<MediaInfo.File>()

    private val gridAdapterDelegate by lazy {
        MediaStaggered.buildDelegate(
            MediaStaggered.ItemAdapter(
                data = mediaData,
                onItemClick = ::onItemClick
            )
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

    private val fragmentHolder by lazy {
        FragmentHolderImpl(
            page = page,
            sortMenuHolder = sortPopupHolder,
            onDataChangedCallback = ::reloadData,
            dataVersionCallback = ::checkDataVersion,
            selectToCallback = ::callSelectTo
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
        binding?.apply {
            gridAdapterDelegate.bind(contentList, activity)
            refreshLayout.setOnRefreshListener {
                refreshData()
            }
        }
    }

    override fun onWindowInsetsChanged(insets: Rect) {
        super.onWindowInsetsChanged(insets)
        binding?.apply {
            refreshLayout.setProgressViewOffset(true, 0, insets.top)
            val actionBarSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                42F,
                root.resources.displayMetrics
            ).toInt()
            val optionBarSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                72F,
                root.resources.displayMetrics
            ).toInt()
            gridAdapterDelegate.onInsetsChanged(
                insets.top + actionBarSize,
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

    override fun onResume() {
        super.onResume()
        callback?.onPageResume(fragmentHolder)
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
    }

    private class FragmentHolderImpl(
        override val page: HomePage,
        private val sortMenuHolder: IconPopupMenu.MenuHolder,
        private val onDataChangedCallback: () -> Unit,
        private val dataVersionCallback: (version: Long) -> Unit,
        private val selectToCallback: (position: Int) -> Unit
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
    }

}