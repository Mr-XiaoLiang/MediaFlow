package com.lollipop.mediaflow.page.main

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.lollipop.common.tools.LLog.Companion.registerLog
import com.lollipop.common.tools.postUI
import com.lollipop.common.ui.page.InsetsFragment
import com.lollipop.common.ui.page.fetchCallback
import com.lollipop.common.ui.view.IconPopupMenu
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MediaSort
import com.lollipop.mediaflow.databinding.FragmentMainMediaBinding
import com.lollipop.mediaflow.databinding.ItemMainHeaderBinding
import com.lollipop.mediaflow.ui.HalfSpaceAdapter
import com.lollipop.mediaflow.ui.HomePage
import com.lollipop.mediaflow.ui.IconMenuWearDialog
import com.lollipop.mediaflow.ui.list.MediaStaggered
import com.lollipop.mediaflow.view.HalfSpace

abstract class BasicMediaGridPage(
    private val page: HomePage
) : InsetsFragment(), IconMenuWearDialog.OnMenuItemClickListener {

    private var binding: FragmentMainMediaBinding? = null

    private val mediaData = ArrayList<MediaInfo.File>()

    private val gridAdapterDelegate by lazy {
        ItemAdapterDelegate(
            header = HeaderAdapter(
                sortTypeProvider = ::getCurrentSortType,
                onSortClick = ::onSortClick,
                onMenuClick = ::onMenuClick
            ),
            content = MediaStaggered.ItemAdapter(
                data = mediaData,
                onItemClick = ::onItemClick
            )
        )
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
            contentList.adapter = gridAdapterDelegate.root
            val layoutManager = StaggeredGridLayoutManager(
                2, StaggeredGridLayoutManager.VERTICAL
            )
            contentList.layoutManager = layoutManager
            refreshLayout.setOnRefreshListener {
                refreshData()
            }
            refreshLayout.setColorSchemeResources(R.color.gallery_focus)
            refreshLayout.setProgressBackgroundColorSchemeResource(R.color.button_background)
        }
    }

    private fun getCurrentSortType(): MediaSort {
        return sortType
    }

    override fun onWindowInsetsChanged(insets: Rect) {
        super.onWindowInsetsChanged(insets)
        binding?.apply {
            refreshLayout.setProgressViewOffset(true, 0, insets.top)
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
            updateSortIcon()
            gridAdapterDelegate.content.notifyDataSetChanged()
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

    override fun onMenuItemClick(item: IconPopupMenu.MenuItemEntity) {
        val mediaSort = MediaSort.findByKey(item.tag)
        if (mediaSort != null) {
            sortType = mediaSort
            reloadData()
            return
        }
        return
    }

    private fun updateSortIcon() {
        gridAdapterDelegate.header.onDataChanged()
    }

    private fun onSortClick(clickedView: View) {
        SortMenu().show(childFragmentManager, "SortMenu")
    }

    private fun onMenuClick(clickedView: View) {
        callback?.onMenuClick(page = page, view = clickedView)
    }

    class SortMenu : IconMenuWearDialog() {
        override fun getMenuList(context: Context): List<IconPopupMenu.MenuItemEntity> {
            return listOf(
                IconPopupMenu.MenuItemEntity(
                    tag = MediaSort.DateDesc.key,
                    titleRes = R.string.sort_date_desc,
                    iconRes = R.drawable.clock_arrow_down_24
                ),
                IconPopupMenu.MenuItemEntity(
                    tag = MediaSort.DateAsc.key,
                    titleRes = R.string.sort_date_asc,
                    iconRes = R.drawable.clock_arrow_up_24
                ),
                IconPopupMenu.MenuItemEntity(
                    tag = MediaSort.NameDesc.key,
                    titleRes = R.string.sort_text_desc,
                    iconRes = R.drawable.text_arrow_down_24
                ),
                IconPopupMenu.MenuItemEntity(
                    tag = MediaSort.NameAsc.key,
                    titleRes = R.string.sort_text_asc,
                    iconRes = R.drawable.text_arrow_up_24
                ),
                IconPopupMenu.MenuItemEntity(
                    tag = MediaSort.Random.key,
                    titleRes = R.string.sort_random,
                    iconRes = R.drawable.shuffle_24
                )
            )
        }

    }

    interface Callback {
        fun onMediaItemClick(page: HomePage, position: Int)
        fun onMenuClick(page: HomePage, view: View)
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
        fun onDataChanged()
        fun checkDataVersion(version: Long)

        fun selectTo(position: Int)
    }

    private class FragmentHolderImpl(
        override val page: HomePage,
        private val onDataChangedCallback: () -> Unit,
        private val dataVersionCallback: (version: Long) -> Unit,
        private val selectToCallback: (position: Int) -> Unit
    ) : FragmentHolder {

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

    private class ItemAdapterDelegate(
        val header: HeaderAdapter,
        val content: MediaStaggered.ItemAdapter
    ) {

        val root = ConcatAdapter(
            header,
            content,
            HalfSpaceAdapter()
        )

    }

    private class HeaderAdapter(
        private val sortTypeProvider: () -> MediaSort,
        private val onSortClick: (clickedView: View) -> Unit,
        private val onMenuClick: (clickedView: View) -> Unit
    ) : RecyclerView.Adapter<HeaderHolder>() {

        private var headerHolder: HeaderHolder? = null

        fun onDataChanged() {
            notifyItemChanged(0)
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): HeaderHolder {
            return headerHolder ?: HeaderHolder(
                ItemMainHeaderBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ),
                sortTypeProvider = sortTypeProvider,
                onSortClick = onSortClick,
                onMenuClick = onMenuClick
            ).also {
                headerHolder = it
            }
        }

        override fun onBindViewHolder(
            holder: HeaderHolder,
            position: Int
        ) {
            holder.onBind()
        }

        override fun getItemCount(): Int {
            return 1
        }

    }

    private class HeaderHolder(
        private val binding: ItemMainHeaderBinding,
        private val sortTypeProvider: () -> MediaSort,
        private val onSortClick: (clickedView: View) -> Unit,
        private val onMenuClick: (clickedView: View) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.sortBtn.setOnClickListener {
                onSortClick(it)
            }
            binding.menuBtn.setOnClickListener {
                onMenuClick(it)
            }
        }

        fun onBind() {
            itemView.post {
                itemView.updateLayoutParams<StaggeredGridLayoutManager.LayoutParams> {
                    isFullSpan = true // 设置为全跨列
                }
            }
            binding.sortBtn.setImageResource(
                when (sortTypeProvider()) {
                    MediaSort.DateDesc -> R.drawable.clock_arrow_down_24
                    MediaSort.DateAsc -> R.drawable.clock_arrow_up_24
                    MediaSort.NameDesc -> R.drawable.text_arrow_down_24
                    MediaSort.NameAsc -> R.drawable.text_arrow_up_24
                    MediaSort.Random -> R.drawable.shuffle_24
                }
            )
        }

    }

}