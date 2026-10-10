package com.lollipop.mediaflow.page.play

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import com.lollipop.mediaflow.data.MediaSource
import com.lollipop.mediaflow.data.SourceLoader
import com.lollipop.mediaflow.data.local.ArchiveQuick
import com.lollipop.mediaflow.data.local.LocalState
import com.lollipop.mediaflow.data.local.MediaInfo
import com.lollipop.mediaflow.data.local.MediaType
import com.lollipop.mediaflow.page.flow.compose.FlowSidePanel
import com.lollipop.mediaflow.page.flow.compose.photo.PhotoFlowComposeScreen
import com.lollipop.mediaflow.tools.ArchiveHelper
import com.lollipop.mediaflow.tools.MediaPlayLauncher
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.mediaflow.ui.BasicFlowComposeActivity
import kotlinx.coroutines.launch

/**
 * 图片浏览页（**纯 Compose 新版**，与旧 [PhotoFlowActivity] 并存）。
 *
 * 与视频页保持同一套数据语义：观察 `MediaSource` 投影（不拷贝列表）、归档走唯一写路径、
 * 索引收敛由列表长度变化统一处理、`onStop` 回传位置。
 *
 * 页面内容见 [PhotoFlowComposeScreen]；归档动作固定使用 `ArchiveQuick.Other`
 * （与旧 `PhotoFlowActivity.onArchiveClick` 一致）。
 *
 * 入口（里程碑 9）：首页图片入口已指向本页（见 `MediaPlayLauncher` 与 manifest）；
 * 旧 `PhotoFlowActivity` 仅保留代码，待人工验证通过后随旧表现层一并删除。
 *
 * 仍待接入：多来源（`SourceId` 固定 Local）。
 */
class PhotoFlowComposeActivity : BasicFlowComposeActivity() {

    private val mediaParams = MediaPlayLauncher.params()

    private val currentIndex = mutableIntStateOf(0)

    /** 侧栏点击产生的跳转请求（[NoScrollRequest] 表示无）。 */
    private val scrollRequest = mutableIntStateOf(NoScrollRequest)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaParams.onCreate(this, savedInstanceState)
        lifecycleScope.launch {
            SourceLoader.Local.fill(
                this@PhotoFlowComposeActivity,
                LocalState.of(mediaParams.visibility, MediaType.Image)
            )
        }
    }

    override fun onStop() {
        super.onStop()
        mediaParams.onSelected(this, currentIndex.intValue)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mediaParams.onSaveInstanceState(this, outState)
    }

    @Composable
    override fun ContentPanel() {
        val source = remember {
            MediaSource.of(mediaParams.visibility, MediaType.Image)
        }
        val photos = source.local.filterIsInstance<MediaInfo.File>()

        LaunchedEffect(photos.size) {
            val lastIndex = photos.lastIndex
            currentIndex.intValue = when {
                lastIndex < 0 -> 0
                currentIndex.intValue > lastIndex -> lastIndex
                else -> currentIndex.intValue
            }
        }

        PhotoFlowComposeScreen(
            items = photos,
            initialIndex = mediaParams.currentPosition,
            // 与旧实现一致：归档按钮的显隐由「快速归档」偏好决定
            isArchiveEnabled = Preferences.isQuickArchiveEnable.get(),
            scrollToIndex = scrollRequest.intValue,
            onScrollRequestHandled = { scrollRequest.intValue = NoScrollRequest },
            onIndexChanged = { index ->
                currentIndex.intValue = index
                mediaParams.onSelected(this, index)
            },
            onArchiveClick = { media -> archive(media) }
        )
    }

    /** 侧栏内容：与主内容区读同一个 `MediaSource` 投影，选中项取自 [currentIndex]。 */
    @Composable
    override fun SidePanel() {
        val source = remember {
            MediaSource.of(mediaParams.visibility, MediaType.Image)
        }
        FlowSidePanel(
            items = source.local.filterIsInstance<MediaInfo.File>(),
            selectedIndex = currentIndex.intValue,
            onItemClick = { index -> scrollRequest.intValue = index }
        )
    }

    private fun archive(media: MediaInfo.File) {
        lifecycleScope.launch {
            ArchiveHelper.remove(
                context = this@PhotoFlowComposeActivity,
                file = media,
                quick = ArchiveQuick.Other,
                visibility = mediaParams.visibility
            ) {
                // 列表由投影自动收敛，无需手工移除
            }
        }
    }
}

/** 无跳转请求。 */
private const val NoScrollRequest = -1
