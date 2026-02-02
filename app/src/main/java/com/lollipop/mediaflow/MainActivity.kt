package com.lollipop.mediaflow

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.util.TypedValue
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MediaSort
import com.lollipop.mediaflow.data.MediaStore
import com.lollipop.mediaflow.data.MediaType
import com.lollipop.mediaflow.data.MediaVisibility
import com.lollipop.mediaflow.databinding.ActivityMainBinding
import com.lollipop.mediaflow.page.PrivateKeySettingActivity
import com.lollipop.mediaflow.page.main.BasicMediaGridPage
import com.lollipop.mediaflow.tools.PrivacyLock
import com.lollipop.mediaflow.ui.BasicInsetsActivity
import com.lollipop.mediaflow.ui.BlueHelper
import com.lollipop.mediaflow.ui.HomePage
import com.lollipop.mediaflow.ui.InsetsFragment

class MainActivity : BasicInsetsActivity(), InsetsFragment.Provider, BasicMediaGridPage.Callback {

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
            it.adapter = SubPageAdapter(this)
            it.isUserInputEnabled = false
        }
        binding.flowButton.setOnClickListener {

        }
        binding.galleryButton.setOnClickListener {

        }
        binding.sortBtnBlur.setOnClickListener {
            focusPageHolder?.onSortClick(it)
        }
        binding.menuBtnBlur.setOnClickListener {
            focusPageHolder?.onMenuClick(it)
        }

        binding.privateVideoTab.isVisible = PrivacyLock.privateVisibility
        binding.privatePhotoTab.isVisible = PrivacyLock.privateVisibility

        updateSortIcon()

        updateBlur()
    }

    override fun onResume() {
        super.onResume()
        if (PrivacyLock.privateSetting) {
            startActivity(Intent(this, PrivateKeySettingActivity::class.java))
        }
    }

    private fun updateBlur() {
        BlueHelper.bind(
            window,
            binding.blurTarget,
            binding.tabBarBlur,
            binding.flowButtonBlur,
            binding.galleryButtonBlur,
            binding.sortBtnBlur,
            binding.menuBtnBlur,
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
        mediaInfo: MediaInfo.File
    ) {
//        getGallery(page)
        Toast.makeText(this, "onMediaItemClick: ${mediaInfo.name}", Toast.LENGTH_SHORT).show()
    }

    override fun onLoad(
        page: HomePage,
        sort: MediaSort,
        callback: (List<MediaInfo.File>) -> Unit
    ) {
        getGallery(page).load(sort) { gallery, _ ->
            updateSortIcon()
            callback(gallery.fileList)
        }
        updateSortIcon()
    }

    override fun onRefresh(
        page: HomePage,
        sort: MediaSort,
        callback: (List<MediaInfo.File>) -> Unit
    ) {
        getGallery(page).refresh(sort) { gallery, _ ->
            updateSortIcon()
            callback(gallery.fileList)
        }
        updateSortIcon()
    }

    override fun onPageResume(holder: BasicMediaGridPage.FragmentHolder) {
        this.focusPageHolder = holder
        updateSortIcon()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateBlur()
    }

    private class SubPageAdapter(
        fragmentActivity: FragmentActivity
    ) : FragmentStateAdapter(fragmentActivity) {

        private val pageArray = HomePage.entries

        override fun createFragment(position: Int): Fragment {
            return pageArray[position].pageClass.getDeclaredConstructor().newInstance()
        }

        override fun getItemCount(): Int {
            return pageArray.size
        }

    }

}