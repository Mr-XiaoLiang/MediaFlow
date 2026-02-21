package com.lollipop.mediaflow

import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.view.Gravity
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.lollipop.mediaflow.data.MediaDirectoryTree
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MediaLayout
import com.lollipop.mediaflow.data.MediaSort
import com.lollipop.mediaflow.data.MediaStore
import com.lollipop.mediaflow.data.MediaType
import com.lollipop.mediaflow.data.MediaVisibility
import com.lollipop.mediaflow.databinding.ActivityMainBinding
import com.lollipop.mediaflow.page.main.BasicMediaGridPage
import com.lollipop.mediaflow.page.settings.RootUriManagerActivity
import com.lollipop.mediaflow.tools.MediaIndex
import com.lollipop.mediaflow.tools.MediaPlayLauncher
import com.lollipop.mediaflow.tools.PrivacyLock
import com.lollipop.mediaflow.ui.BasicInsetsActivity
import com.lollipop.mediaflow.ui.BlurHelper
import com.lollipop.mediaflow.ui.DirectoryChooseDialog
import com.lollipop.mediaflow.ui.HomePage
import com.lollipop.mediaflow.ui.IconPopupMenu
import com.lollipop.mediaflow.ui.InsetsFragment

class MainActivity : BasicInsetsActivity(), InsetsFragment.Provider, BasicMediaGridPage.Callback,
    DirectoryChooseDialog.OnFolderClickListener {

    companion object {
        private const val KEY_SOURCE_MANAGER = "SourceManager"
        private const val KEY_PRIVATE_KEY_MANAGER = "PrivateKeyManager"
        private const val KEY_DEBUG_MODE = "DebugMode"
    }

    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private val insetsProviderHelper = InsetsFragment.ProviderHelper()

    private val publicPhotoGallery by lazy {
        MediaStore.loadGallery(this, MediaVisibility.Public, MediaType.Image)
    }

    private val publicVideoGallery by lazy {
        MediaStore.loadGallery(this, MediaVisibility.Public, MediaType.Video)
    }

    private val privatePhotoGallery by lazy {
        MediaStore.loadGallery(this, MediaVisibility.Private, MediaType.Image)
    }

    private val privateVideoGallery by lazy {
        MediaStore.loadGallery(this, MediaVisibility.Private, MediaType.Video)
    }

    private var focusPageHolder: BasicMediaGridPage.FragmentHolder? = null

    private var currentPage = HomePage.PublicVideo

    private val optionPopupHolder by lazy {
        IconPopupMenu.hold(::buildOptionMenu)
    }

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
        binding.tabGroup.select(0)
        binding.publicVideoTab.setOnClickListener {
            selectTab(PrivacyLock.IconKey.VIDEO, 0)
        }
        binding.publicPhotoTab.setOnClickListener {
            selectTab(PrivacyLock.IconKey.PHOTO, 1)
        }
        binding.privateVideoTab.setOnClickListener {
            selectTab(PrivacyLock.IconKey.VIDEO, 2)
        }
        binding.privatePhotoTab.setOnClickListener {
            selectTab(PrivacyLock.IconKey.PHOTO, 3)
        }
        binding.viewPager2.also {
            val pageAdapter = SubPageAdapter(this)
            it.adapter = pageAdapter
            it.isUserInputEnabled = false
            it.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    currentPage = pageAdapter.getPage(position)
                }
            })
        }
        binding.flowButton.setOnClickListener {
            openPlayPage(MediaLayout.Flow)
        }
        binding.galleryButton.setOnClickListener {
            openPlayPage(MediaLayout.Gallery)
        }
        binding.sortBtn.setOnClickListener {
            focusPageHolder?.onSortClick(it)
        }
        binding.menuBtn.setOnClickListener {
            onMenuClick(it)
        }
        binding.dirBtn.setOnClickListener {
            DirectoryChooseDialog.create(currentPage.visibility, currentPage.mediaType)
                .show(supportFragmentManager, "DirectoryChooseDialog")
        }

        binding.privateVideoTab.isVisible = PrivacyLock.privateVisibility
        binding.privatePhotoTab.isVisible = PrivacyLock.privateVisibility

        updateSortIcon()

        updateBlur()

        dataChangedListener.register(
            MediaStore.loadStore(this, MediaVisibility.Public),
            MediaStore.loadStore(this, MediaVisibility.Private)
        )
    }

    override fun onResume() {
        super.onResume()
        if (PrivacyLock.privateSetting) {
            PrivacyLock.openPrivateKeyManager(this)
        }
    }

    private fun onMenuClick(clickedView: View) {
        optionPopupHolder.show(clickedView)
    }

    private fun onPlayResult(index: MediaIndex) {
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
            .addMenu(
                tag = KEY_DEBUG_MODE,
                titleRes = R.string.debug_mode,
                iconRes = 0
            )
            .filter { item ->
                when (item.tag) {
                    KEY_PRIVATE_KEY_MANAGER -> {
                        PrivacyLock.privateVisibility
                    }

                    KEY_DEBUG_MODE -> {
                        packageName.endsWith(".debug")
                    }

                    else -> {
                        true
                    }
                }
            }
            .gravity(Gravity.END)
            .offsetDp(0, 8)
            .onClick {
                when (it.tag) {
                    KEY_SOURCE_MANAGER -> {
                        RootUriManagerActivity.start(this, visibility = currentPage.visibility)
                        true
                    }

                    KEY_PRIVATE_KEY_MANAGER -> {
                        PrivacyLock.openPrivateKeyManager(this)
                        true
                    }

                    else -> {
                        false
                    }
                }
            }
    }

    private fun openPlayPage(layout: MediaLayout, index: Int = 0) {
        playLauncher.launch(
            visibility = currentPage.visibility,
            type = currentPage.mediaType,
            position = index,
            layout = layout
        )
    }

    private fun updateBlur() {
        BlurHelper.bind(
            window,
            binding.blurTarget,
            binding.tabBarBlur,
            binding.flowButtonBlur,
            binding.galleryButtonBlur,
            binding.sortBtnBlur,
            binding.menuBtnBlur,
            binding.dirBtnBlur
        )
    }

    private fun selectTab(iconKey: PrivacyLock.IconKey, index: Int) {
        PrivacyLock.feed(iconKey)
        binding.tabGroup.select(index)
        binding.viewPager2.setCurrentItem(index, false)
        binding.privateVideoTab.isVisible = PrivacyLock.privateVisibility
        binding.privatePhotoTab.isVisible = PrivacyLock.privateVisibility
    }

    private fun initInsetsListener() {
        initInsetsListener(binding.main)
        binding.tabBar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            insetsProviderHelper.updateInsets(
                bottom = binding.viewPager2.bottom - binding.tabBar.top
            )
        }
        bindGuidelineInsets(
            leftGuideline = binding.startGuideLine,
            topGuideline = binding.topGuideLine,
            rightGuideline = binding.endGuideLine,
            bottomGuideline = binding.bottomGuideLine,
        )
    }

    override fun onWindowInsetsChanged(left: Int, top: Int, right: Int, bottom: Int) {
        insetsProviderHelper.updateInsets(left = left, top = top, right = right)
    }

    override fun getInsets(): Rect {
        return insetsProviderHelper.getInsets()
    }

    override fun registerInsetsListener(listener: InsetsFragment.InsetsListener) {
        insetsProviderHelper.registerInsetsListener(listener)
    }

    override fun unregisterInsetsListener(listener: InsetsFragment.InsetsListener) {
        insetsProviderHelper.unregisterInsetsListener(listener)
    }

    private fun getGallery(page: HomePage): MediaStore.Gallery {
        return when (page) {
            HomePage.PublicVideo -> publicVideoGallery
            HomePage.PublicPhoto -> publicPhotoGallery
            HomePage.PrivateVideo -> privateVideoGallery
            HomePage.PrivatePhoto -> privatePhotoGallery
        }
    }

    private fun findFocusPageSortType(): MediaSort? {
        val holder = focusPageHolder ?: return null
        return getGallery(holder.page).sortType
    }

    private fun updateSortIcon() {
        val sortType = findFocusPageSortType()
        if (sortType == null) {
            binding.sortBtn.isVisible = false
        } else {
            binding.sortBtn.isVisible = true
            binding.sortIcon.setImageResource(
                when (sortType) {
                    MediaSort.DateDesc -> R.drawable.clock_arrow_down_24
                    MediaSort.DateAsc -> R.drawable.clock_arrow_up_24
                    MediaSort.NameDesc -> R.drawable.text_arrow_down_24
                    MediaSort.NameAsc -> R.drawable.text_arrow_up_24
                    MediaSort.Random -> R.drawable.shuffle_24
                }
            )
        }
    }

    override fun onMediaItemClick(
        page: HomePage,
        position: Int,
        type: MediaLayout
    ) {
        openPlayPage(layout = type, index =  position)
    }

    override fun onLoad(
        page: HomePage,
        sort: MediaSort,
        callback: (version: Long, List<MediaInfo.File>) -> Unit
    ) {
        getGallery(page).load(sort) { gallery, _ ->
            updateSortIcon()
            callback(gallery.store.dataVersion, gallery.fileList)
        }
        updateSortIcon()
    }

    override fun onRefresh(
        page: HomePage,
        sort: MediaSort,
        callback: (version: Long, List<MediaInfo.File>) -> Unit
    ) {
        getGallery(page).refresh(sort) { gallery, _ ->
            updateSortIcon()
            callback(gallery.store.dataVersion, gallery.fileList)
        }
        updateSortIcon()
    }

    override fun onPageResume(holder: BasicMediaGridPage.FragmentHolder) {
        this.focusPageHolder = holder
        updateSortIcon()
        holder.checkDataVersion(
            dataChangedListener.currentDataVersion(getGallery(holder.page).store)
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateBlur()
    }

    override fun onFolderClick(folder: MediaDirectoryTree?) {
        this.focusPageHolder?.let { holder ->
            getGallery(holder.page).setRootDirectory(folder)
            holder.onDataChanged()
        }
    }

    private fun onDataChanged(store: MediaStore) {
        this.focusPageHolder?.let { holder ->
            if (holder.page.visibility == store.visibility) {
                holder.onDataChanged()
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

}