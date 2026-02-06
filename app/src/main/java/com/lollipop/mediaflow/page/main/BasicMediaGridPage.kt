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
import com.lollipop.mediaflow.page.RootUriManagerActivity
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.PrivacyLock
import com.lollipop.mediaflow.ui.HomePage
import com.lollipop.mediaflow.ui.IconPopupMenu
import com.lollipop.mediaflow.ui.InsetsFragment
import com.lollipop.mediaflow.ui.MediaGridAdapter

abstract class BasicMediaGridPage(
    private val page: HomePage
) : InsetsFragment() {

    companion object {
        private const val KEY_SOURCE_MANAGER = "SourceManager"
        private const val KEY_PRIVATE_KEY_MANAGER = "PrivateKeyManager"
    }

    private var binding: FragmentMainMediaBinding? = null

    private val mediaData = ArrayList<MediaInfo.File>()

    private val gridAdapterDelegate by lazy {
        MediaGridAdapter.buildDelegate(MediaGridAdapter.MediaItemAdapter(mediaData, ::onItemClick))
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

    private var loadCount = 0

    private val fragmentHolder by lazy {
        FragmentHolderImpl(
            page = page,
            sortMenuHolder = sortPopupHolder,
            optionMenuHolder = optionPopupHolder
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
//            contentList.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            refreshLayout.setProgressViewOffset(true, 0, insets.top)
            val actionBarSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                42f,
                root.resources.displayMetrics
            ).toInt()
            gridAdapterDelegate.onInsetsChanged(
                insets.top + actionBarSize,
                insets.bottom + actionBarSize
            )
            contentList.setPadding(insets.left, 0, insets.right, 0)
        }
    }

    override fun onResume() {
        super.onResume()
        callback?.onPageResume(fragmentHolder)
        if (mediaData.isEmpty()) {
            if (loadCount < 1) {
                refreshData()
            } else {
                reloadData()
            }
        }
    }

    private fun reloadData() {
        callback?.onLoad(page = page, sort = sortType, callback = ::onDataLoaded)
    }

    private fun refreshData() {
        loadCount++
        binding?.refreshLayout?.isRefreshing = true
        callback?.onRefresh(page = page, sort = sortType, callback = ::onDataLoaded)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun onDataLoaded(mediaList: List<MediaInfo.File>) {
        mediaData.clear()
        mediaData.addAll(mediaList)
        gridAdapterDelegate.notifyContentDataSetChanged()
        binding?.refreshLayout?.isRefreshing = false
        log.i("onDataLoaded, mediaList.size=${mediaList.size}")
    }

    private fun onItemClick(mediaInfo: MediaInfo.File, position: Int) {
        callback?.onMediaItemClick(page = page, mediaInfo = mediaInfo, position = position)
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

    private fun buildOptionMenu(builder: IconPopupMenu.Builder) {
        builder
            .addMenu(
                tag = KEY_SOURCE_MANAGER,
                titleRes = R.string.source_manager,
                iconRes = 0
            )
            .addMenu(
                tag = KEY_PRIVATE_KEY_MANAGER,
                titleRes = R.string.private_key_manager,
                iconRes = 0
            )
            .filter { item ->
                if (item.tag == KEY_PRIVATE_KEY_MANAGER) {
                    PrivacyLock.privateVisibility
                } else {
                    true
                }
            }
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

                    KEY_PRIVATE_KEY_MANAGER -> {
                        activity?.let { act ->
                            PrivacyLock.openPrivateKeyManager(act)
                        }
                        true
                    }

                    else -> {
                        false
                    }
                }
            }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        gridAdapterDelegate.updateSpanCount(activity)
    }

    interface Callback {
        fun onMediaItemClick(page: HomePage, mediaInfo: MediaInfo.File, position: Int)
        fun onLoad(page: HomePage, sort: MediaSort, callback: (List<MediaInfo.File>) -> Unit)
        fun onRefresh(page: HomePage, sort: MediaSort, callback: (List<MediaInfo.File>) -> Unit)
        fun onPageResume(holder: FragmentHolder)
    }

    interface FragmentHolder {
        fun onMenuClick(clickedView: View)
        fun onSortClick(clickedView: View)
        val page: HomePage
    }

    private class FragmentHolderImpl(
        override val page: HomePage,
        private val sortMenuHolder: IconPopupMenu.MenuHolder,
        private val optionMenuHolder: IconPopupMenu.MenuHolder,
    ) : FragmentHolder {
        override fun onMenuClick(clickedView: View) {
            optionMenuHolder.show(clickedView)
        }

        override fun onSortClick(clickedView: View) {
            sortMenuHolder.show(clickedView)
        }
    }

}