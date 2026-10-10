package com.lollipop.mediaflow

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.lollipop.common.tools.BiometricAuthHelper
import com.lollipop.mediaflow.data.SourceLoader
import com.lollipop.mediaflow.data.local.MediaDirectoryTree
import com.lollipop.mediaflow.page.archive.ArchiveRenameActivity
import com.lollipop.mediaflow.page.settings.PreferencesActivity
import com.lollipop.mediaflow.page.settings.RootUriManagerActivity
import com.lollipop.mediaflow.page.tools.VideoDuplicateFinderActivity
import com.lollipop.mediaflow.tools.MediaIndex
import com.lollipop.mediaflow.tools.MediaPlayLauncher
import com.lollipop.mediaflow.tools.PrivacyLock
import com.lollipop.mediaflow.ui.DirectoryChooseDialog
import com.lollipop.mediaflow.ui.HomePage
import com.lollipop.mediaflow.ui.home.HomePageActions
import com.lollipop.mediaflow.ui.home.HomeScreen
import com.lollipop.mediaflow.ui.home.HomeScrollBus
import com.lollipop.mediaflow.ui.home.menu.HomeMenuKeys
import com.lollipop.mediaflow.ui.theme.MediaFlowTheme
import kotlinx.coroutines.launch

/**
 * 首页宿主：纯 Compose 内容（[HomeScreen]）。
 * 保留 AppCompatActivity 是为了继续使用 supportFragmentManager 承载文件夹选择对话框。
 */
class MainActivity : AppCompatActivity(), HomePageActions,
    DirectoryChooseDialog.OnFolderClickListener {

    private var currentPage = HomePage.PublicVideo

    private var folderTargetPage: HomePage? = null

    private var isPrivacyLockBiometric = false

    private val privateLockController = PrivacyLock.controller(::filterPrivacyLock)

    private val playLauncher by lazy {
        MediaPlayLauncher { result ->
            if (result != null) {
                onPlayResult(result)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediaFlowTheme {
                HomeScreen(actions = this)
            }
        }
        playLauncher.register(this)
        checkUpdate()
    }

    override fun onResume() {
        super.onResume()
        isPrivacyLockBiometric = PrivacyLock.isPrivateLockBiometric(this)
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

    private fun onPlayResult(index: MediaIndex) {
        HomeScrollBus.request(
            page = HomePage.findPage(index.visibility, index.type),
            position = index.position
        )
    }

    override fun onMediaClick(page: HomePage, index: Int) {
        currentPage = page
        openPlayPage(index = index)
    }

    override fun onSloganLongClick(page: HomePage) {
        currentPage = page
        privateLockController.requestToggle()
    }

    override fun onFolderSelect(page: HomePage) {
        currentPage = page
        folderTargetPage = page
        DirectoryChooseDialog.create(page.visibility, page.mediaType)
            .show(supportFragmentManager, "DirectoryChooseDialog")
    }

    override fun onAddSource(page: HomePage) {
        currentPage = page
        RootUriManagerActivity.start(this, visibility = page.visibility)
    }

    override fun onMenuAction(page: HomePage, tag: String) {
        currentPage = page
        when (tag) {
            HomeMenuKeys.SOURCE_MANAGER -> {
                RootUriManagerActivity.start(this, visibility = page.visibility)
            }

            HomeMenuKeys.PREFERENCES -> {
                PreferencesActivity.start(this)
            }

            HomeMenuKeys.VIDEO_DUPLICATE -> {
                VideoDuplicateFinderActivity.start(this, page = page)
            }

            HomeMenuKeys.ARCHIVE_RENAME -> {
                ArchiveRenameActivity.start(this)
            }

            else -> {
                // Debug 等其它入口暂未绑定动作
            }
        }
    }

    private fun openPlayPage(index: Int = 0) {
        playLauncher.launch(
            visibility = currentPage.visibility,
            type = currentPage.mediaType,
            position = index
        )
    }

    private fun filterPrivacyLock(locked: Boolean, callback: (Boolean) -> Unit) {
        if (!isPrivacyLockBiometric) {
            // 关闭的情况下，或者不支持的情况下，就不验证了
            callback(locked)
            return
        }
        if (locked) {
            // 锁定不需要验证
            callback(true)
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
            }
        }
    }

    override fun onFolderClick(folder: MediaDirectoryTree?) {
        val page = folderTargetPage ?: currentPage
        folderTargetPage = null
        val state = page.localState
        // 只是变更范围筛选，重新投影即可（fill 从缓存筛选，不做扫描）
        state.setScopeId(folder?.id ?: "")
        lifecycleScope.launch {
            SourceLoader.Local.fill(this@MainActivity, state)
        }
    }

}
