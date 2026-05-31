package com.lollipop.mediaflow

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.lollipop.common.ui.page.BasicInsetsActivity
import com.lollipop.common.ui.view.IconPopupMenu
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MediaSort
import com.lollipop.mediaflow.data.MediaStore
import com.lollipop.mediaflow.data.MediaType
import com.lollipop.mediaflow.data.MediaVisibility
import com.lollipop.mediaflow.databinding.ActivityMainBinding
import com.lollipop.mediaflow.page.main.BasicMediaGridPage
import com.lollipop.mediaflow.page.settings.PreferencesActivity
import com.lollipop.mediaflow.page.settings.RootUriManagerActivity
import com.lollipop.mediaflow.tools.MediaIndex
import com.lollipop.mediaflow.tools.MediaPlayLauncher
import com.lollipop.mediaflow.ui.HomePage
import com.lollipop.mediaflow.ui.IconMenuWearDialog

class MainActivity : BasicInsetsActivity(), BasicMediaGridPage.Callback,
    IconMenuWearDialog.OnMenuItemClickListener {

    companion object {
        private const val KEY_SOURCE_MANAGER = "SourceManager"
        private const val KEY_DEBUG_MODE = "DebugMode"
        private const val KEY_PREFERENCES = "Preferences"
    }

    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private val publicPhotoGallery by lazy {
        MediaStore.loadGallery(this, MediaVisibility.Public, MediaType.Image)
    }

    private val publicVideoGallery by lazy {
        MediaStore.loadGallery(this, MediaVisibility.Public, MediaType.Video)
    }

    private var focusPageHolder: BasicMediaGridPage.FragmentHolder? = null

    private var currentPage = HomePage.PublicVideo

    private val playLauncher by lazy {
        MediaPlayLauncher { result ->
            if (result != null) {
                onPlayResult(result)
            }
        }
    }

    private val dataChangedListener by lazy {
        MediaStore.createListener(this, ::onDataChanged)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        initInsetsListener()
        binding.viewPager2.also {
            val pageAdapter = SubPageAdapter(this)
            it.adapter = pageAdapter
            it.offscreenPageLimit = pageAdapter.itemCount
            it.isUserInputEnabled = true
            it.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    currentPage = pageAdapter.getPage(position)
                }
            })
        }
        playLauncher.register(this)
        dataChangedListener.register(
            MediaStore.loadStore(this, MediaVisibility.Public),
            MediaStore.loadStore(this, MediaVisibility.Private)
        )
        binding.pageIndicator.bind(binding.viewPager2)
    }

    private fun onPlayResult(index: MediaIndex) {
        focusPageHolder?.selectTo(index.position)
    }

    private fun openPlayPage(index: Int = 0) {
        playLauncher.launch(
            visibility = currentPage.visibility,
            type = currentPage.mediaType,
            position = index,
        )
    }

    private fun initInsetsListener() {
        initInsetsListener(binding.main)
    }

    private fun getGallery(page: HomePage): MediaStore.Gallery {
        return when (page) {
            HomePage.PublicVideo -> publicVideoGallery
            HomePage.PublicPhoto -> publicPhotoGallery
        }
    }

    private fun findFocusPageSortType(): MediaSort? {
        val holder = focusPageHolder ?: return null
        return getGallery(holder.page).sortType
    }

    override fun onMediaItemClick(
        page: HomePage,
        position: Int,
    ) {
        openPlayPage(index = position)
    }

    override fun onMenuClick(page: HomePage, view: View) {
        MenuDialog().show(supportFragmentManager, "MenuDialog")
    }

    override fun onLoad(
        page: HomePage,
        sort: MediaSort,
        callback: (version: Long, List<MediaInfo.File>) -> Unit
    ) {
        getGallery(page).loadChoose(sort) { gallery, _ ->
            callback(gallery.store.dataVersion, gallery.fileList)
        }
    }

    override fun onRefresh(
        page: HomePage,
        sort: MediaSort,
        callback: (version: Long, List<MediaInfo.File>) -> Unit
    ) {
        getGallery(page).refresh(sort) { gallery, _ ->
            callback(gallery.store.dataVersion, gallery.fileList)
        }
    }

    override fun onPageResume(holder: BasicMediaGridPage.FragmentHolder) {
        this.focusPageHolder = holder
        val store = getGallery(holder.page).store
        holder.checkDataVersion(store.dataVersion)
    }

    private fun onDataChanged(store: MediaStore) {
        this.focusPageHolder?.let { holder ->
            if (holder.page.visibility == store.visibility) {
                holder.checkDataVersion(store.dataVersion)
            }
        }
    }

    override fun onWindowInsetsChanged(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
    }

    override fun onMenuItemClick(item: IconPopupMenu.MenuItemEntity) {
        when (item.tag) {
            KEY_SOURCE_MANAGER -> {
                RootUriManagerActivity.start(this, visibility = currentPage.visibility)
            }

            KEY_PREFERENCES -> {
                PreferencesActivity.start(this)
            }

            else -> {
            }
        }
    }

    private class SubPageAdapter(
        fragmentActivity: FragmentActivity
    ) : FragmentStateAdapter(fragmentActivity) {

        private val pageArray = HomePage.entries

        fun getPage(position: Int): HomePage {
            return pageArray[position]
        }

        override fun createFragment(position: Int): Fragment {
            return pageArray[position].pageClass.getDeclaredConstructor().newInstance()
        }

        override fun getItemCount(): Int {
            return pageArray.size
        }

    }

    class MenuDialog : IconMenuWearDialog() {
        override fun getMenuList(context: Context): List<IconPopupMenu.MenuItemEntity> {
            val list = arrayListOf(
                IconPopupMenu.MenuItemEntity(
                    tag = KEY_SOURCE_MANAGER,
                    titleRes = R.string.source_manager,
                    iconRes = 0
                ),
                IconPopupMenu.MenuItemEntity(
                    tag = KEY_PREFERENCES,
                    titleRes = R.string.preferences,
                    iconRes = 0
                )
            )
            if (context.packageName.endsWith(".debug")) {
                list.add(
                    IconPopupMenu.MenuItemEntity(
                        tag = KEY_DEBUG_MODE,
                        titleRes = R.string.debug_mode,
                        iconRes = 0
                    )
                )
            }
            return list
        }

    }

}