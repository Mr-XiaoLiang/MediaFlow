package com.lollipop.mediaflow

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.lollipop.common.tools.BiometricAuthHelper
import com.lollipop.common.ui.page.BasicInsetsActivity
import com.lollipop.common.ui.page.GuidelineInsetsHelper
import com.lollipop.common.ui.view.BlurHelper
import com.lollipop.common.ui.view.IconPopupMenu
import com.lollipop.mediaflow.data.local.MediaDirectoryTree
import com.lollipop.mediaflow.data.local.MediaInfo
import com.lollipop.mediaflow.data.local.MediaSort
import com.lollipop.mediaflow.data.local.MediaStore
import com.lollipop.mediaflow.data.local.MediaType
import com.lollipop.mediaflow.data.local.MediaVisibility
import com.lollipop.mediaflow.databinding.ActivityMainBinding
import com.lollipop.mediaflow.page.archive.ArchiveActivity
import com.lollipop.mediaflow.page.archive.ArchiveRenameActivity
import com.lollipop.mediaflow.page.main.BasicMediaGridPage
import com.lollipop.mediaflow.page.settings.PreferencesActivity
import com.lollipop.mediaflow.page.settings.RootUriManagerActivity
import com.lollipop.mediaflow.page.tools.VideoDuplicateFinderActivity
import com.lollipop.mediaflow.tools.MediaIndex
import com.lollipop.mediaflow.tools.MediaPlayLauncher
import com.lollipop.mediaflow.tools.PrivacyLock
import com.lollipop.mediaflow.ui.DirectoryChooseDialog
import com.lollipop.mediaflow.ui.HomePage
import kotlinx.coroutines.launch

class MainActivity : BasicInsetsActivity(), BasicMediaGridPage.Callback,
    DirectoryChooseDialog.OnFolderClickListener {

    companion object {
        private const val KEY_SOURCE_MANAGER = "SourceManager"
        private const val KEY_PRIVATE_KEY_MANAGER = "PrivateKeyManager"
        private const val KEY_DEBUG_MODE = "DebugMode"
        private const val KEY_PREFERENCES = "Preferences"
        private const val KEY_ARCHIVE = "Archive"
        private const val KEY_VIDEO_DUPLICATE = "VideoDuplicate"
        private const val KEY_ARCHIVE_RENAME = "ArchiveRename"
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

    private val privatePhotoGallery by lazy {
        MediaStore.loadGallery(this, MediaVisibility.Private, MediaType.Image)
    }

    private val privateVideoGallery by lazy {
        MediaStore.loadGallery(this, MediaVisibility.Private, MediaType.Video)
    }

    private var focusPageHolder: BasicMediaGridPage.FragmentHolder? = null

    private var currentPage = HomePage.PublicVideo

    private var isPrivacyLockBiometric = false

    private val privateLockController = PrivacyLock.controller(::filterPrivacyLock)

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

    private val contentInsetsHelper = GuidelineInsetsHelper()

    private val dataChangedListener by lazy {
        MediaStore.createListener(this, ::onDataChanged)
    }

    private val blurHelper = BlurHelper.create()

    private val pageAdapter by lazy {
        SubPageAdapter(this)
    }

    private fun checkUpdate() {
        lifecycleScope.launch {
//            val hasUpdate = GithubApiModel.fetchToday().hasUpdate(BuildConfig.VERSION_CODE)
//            val dotColor = if (hasUpdate) {
//                ContextCompat.getColor(this@MainActivity, R.color.button_slider)
//            } else {
//                ContextCompat.getColor(this@MainActivity, R.color.button_text)
//            }
//            binding.menuBtnIconDot.imageTintList = ColorStateList.valueOf(dotColor)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        initInsetsListener()
        binding.tabGroup.select(0)
        binding.viewPager2.also {
            it.adapter = pageAdapter
            it.offscreenPageLimit = pageAdapter.allPageCount
            it.isUserInputEnabled = true
            pageAdapter.onPriorityLockChanged(PrivacyLock.isLocked)
            it.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    currentPage = pageAdapter.getPage(position)
                    binding.tabGroup.select(position)
                }
            })
        }
//        binding.sortBtn.setOnClickListener {
//            focusPageHolder?.onSortClick(it)
//        }
//        binding.menuBtn.setOnClickListener {
//            onMenuClick(it)
//        }
//        binding.dirBtn.setOnClickListener {
//            DirectoryChooseDialog.create(currentPage.visibility, currentPage.mediaType)
//                .show(supportFragmentManager, "DirectoryChooseDialog")
//        }

        val privateVisibility = PrivacyLock.privateVisibility
        binding.privateVideoTab.isVisible = privateVisibility
        binding.privatePhotoTab.isVisible = privateVisibility

        blurHelper.bind(binding.viewPager2)

        updateSortIcon()

        updateBlur()

        playLauncher.register(this)

        dataChangedListener.register(
            MediaStore.loadStore(this, MediaVisibility.Public),
            MediaStore.loadStore(this, MediaVisibility.Private)
        )
    }

    override fun onResume() {
        super.onResume()
        checkUpdate()
        isPrivacyLockBiometric = PrivacyLock.isPrivateLockBiometric(this)
    }

    private fun onMenuClick(clickedView: View) {
        optionPopupHolder.show(clickedView)
    }

    private fun onPlayResult(index: MediaIndex) {
        focusPageHolder?.selectTo(index.position)
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
                tag = KEY_PREFERENCES,
                titleRes = R.string.preferences,
                iconRes = 0
            )
            .addMenu(
                tag = KEY_ARCHIVE,
                titleRes = R.string.archive,
                iconRes = 0
            )
            .addMenu(
                tag = KEY_ARCHIVE_RENAME,
                titleRes = R.string.archive_rename,
                iconRes = 0
            )
            .addMenu(
                tag = KEY_VIDEO_DUPLICATE,
                titleRes = R.string.label_video_duplicate,
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

                    KEY_VIDEO_DUPLICATE -> {
                        currentPage.mediaType == MediaType.Video
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

                    KEY_PREFERENCES -> {
                        PreferencesActivity.start(this)
                        true
                    }

                    KEY_ARCHIVE -> {
                        ArchiveActivity.start(
                            this,
                            visibility = currentPage.visibility,
                            type = currentPage.mediaType
                        )
                        true
                    }

                    KEY_VIDEO_DUPLICATE -> {
                        VideoDuplicateFinderActivity.start(this, page = currentPage)
                        true
                    }

                    KEY_ARCHIVE_RENAME -> {
                        ArchiveRenameActivity.start(this)
                        true
                    }

                    else -> {
                        false
                    }
                }
            }
    }

    private fun openPlayPage(index: Int = 0) {
        playLauncher.launch(
            visibility = currentPage.visibility,
            type = currentPage.mediaType,
            position = index,
        )
    }

    private fun updateBlur() {
        blurHelper.update(
            window,
            binding.tabBarBlur,
        )
    }

    private fun onPrivacyChanged(isLocked: Boolean) {
        val oldPrivateVisibility = binding.privateVideoTab.isVisible
        val privateVisibility = !isLocked
        binding.privateVideoTab.isVisible = privateVisibility
        binding.privatePhotoTab.isVisible = privateVisibility
        if (oldPrivateVisibility != privateVisibility) {
            binding.viewPager2.setCurrentItem(0, false)
            pageAdapter.onPriorityLockChanged(isLocked)
        }
    }

    private fun filterPrivacyLock(locked: Boolean, callback: (Boolean) -> Unit) {
        if (!isPrivacyLockBiometric) {
            // 关闭的情况下，或者不支持的情况下，就不验证了
            callback(locked)
            onPrivacyChanged(locked)
            return
        }
        if (locked) {
            // 锁定不需要验证
            callback(true)
            onPrivacyChanged(true)
            return
        }
        // 否则就经过验证
        BiometricAuthHelper.authenticate(
            activity = this,
            title = getString(R.string.title_biometric_auth),
            subtitle = getString(R.string.app_name)
        ) {
            if (it is BiometricAuthHelper.AuthResult.Success) {
                callback(locked)
                onPrivacyChanged(locked)
            }
        }
    }

    private fun initInsetsListener() {
        initInsetsListener(binding.main)
        binding.tabBar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            insetsProviderHelper.updateInsets(
                bottom = binding.viewPager2.bottom - binding.tabBar.top
            )
        }
        registerGuidelineInsetsListener(contentInsetsHelper)
        contentInsetsHelper.bindGuidelineInsets(
            leftGuideline = binding.startGuideLine,
            topGuideline = binding.topGuideLine,
            rightGuideline = binding.endGuideLine,
            bottomGuideline = binding.bottomGuideLine,
        )
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
//        if (sortType == null) {
//            binding.sortBtn.isInvisible = true
//        } else {
//            binding.sortBtn.isVisible = true
//            binding.sortBtn.setImageResource(
//                when (sortType) {
//                    MediaSort.DateDesc -> R.drawable.clock_arrow_down_24
//                    MediaSort.DateAsc -> R.drawable.clock_arrow_up_24
//                    MediaSort.NameDesc -> R.drawable.text_arrow_down_24
//                    MediaSort.NameAsc -> R.drawable.text_arrow_up_24
//                    MediaSort.Random -> R.drawable.shuffle_24
//                }
//            )
//        }
    }

    override fun onMediaItemClick(
        page: HomePage,
        position: Int,
    ) {
        openPlayPage(index = position)
    }

    override fun onLoad(
        page: HomePage,
        sort: MediaSort,
        callback: (version: Long, List<MediaInfo.File>) -> Unit
    ) {
        getGallery(page).loadChoose(sort) { gallery, _ ->
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
        val store = getGallery(holder.page).store
        holder.checkDataVersion(store.dataVersion)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateBlur()
    }

    override fun onFolderClick(folder: MediaDirectoryTree?) {
        this.focusPageHolder?.let { holder ->
            getGallery(holder.page).setRootDirectory(folder?.id ?: "")
            holder.onDataChanged()
        }
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

    private class SubPageAdapter(
        fragmentActivity: FragmentActivity
    ) : FragmentStateAdapter(fragmentActivity) {

        private var isPriorityLock = true

        private val pageArray = HomePage.entries

        val allPageCount: Int
            get() {
                return pageArray.size
            }

        @SuppressLint("NotifyDataSetChanged")
        fun onPriorityLockChanged(isLocked: Boolean) {
            isPriorityLock = isLocked
            notifyDataSetChanged()
        }

        fun getPage(position: Int): HomePage {
            return pageArray[position]
        }

        override fun createFragment(position: Int): Fragment {
            return pageArray[position].pageClass.getDeclaredConstructor().newInstance()
        }

        override fun getItemCount(): Int {
            if (isPriorityLock) {
                return allPageCount / 2
            }
            return allPageCount
        }

    }

}