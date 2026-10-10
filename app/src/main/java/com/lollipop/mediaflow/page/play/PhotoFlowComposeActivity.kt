package com.lollipop.mediaflow.page.play

import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.lifecycleScope
import com.lollipop.mediaflow.data.MediaSource
import com.lollipop.mediaflow.data.SourceLoader
import com.lollipop.mediaflow.data.local.ArchiveQuick
import com.lollipop.mediaflow.data.local.LocalState
import com.lollipop.mediaflow.data.local.MediaInfo
import com.lollipop.mediaflow.data.local.MediaType
import com.lollipop.mediaflow.page.flow.compose.FlowSidePanel
import com.lollipop.mediaflow.page.flow.compose.photo.FlowPhotoPreview
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

    /** 当前打开的整图预览（`null` = 未打开）。 */
    private var preview by mutableStateOf<PreviewParams?>(null)

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
            onArchiveClick = { media -> archive(media) },
            // 预览交给 overlay 层：它位于脚手架之外，可覆盖侧栏且独占触摸
            onPreview = { uri, ratio, origin ->
                preview = PreviewParams(uri = uri, ratio = ratio, origin = origin)
            }
        )
    }

    /**
     * 整图预览浮层（[BasicFlowComposeActivity.Overlay]）。
     *
     * 位于脚手架**之外**、铺满整屏：因此不会被侧栏让位收窄，触摸也不会漏给侧栏。
     * 进出场动画的几何由列表回传的「源 item 完整矩形」推导，详见 [FlowPhotoPreview]。
     */
    @Composable
    override fun Overlay() {
        val params = preview ?: return
        FlowPhotoPreview(
            uri = params.uri,
            ratio = params.ratio,
            origin = params.origin,
            onDismiss = { preview = null }
        )
    }

    /** 整图预览的启动参数（源 item 的显示比例与完整矩形）。 */
    private data class PreviewParams(
        val uri: Uri,
        /** 源 item 的显示比例；`null` = 元数据未就绪（预览层退化为纯淡入）。 */
        val ratio: Float?,
        /** 源 item 在根坐标系中的完整矩形（可能部分在屏幕外，不做裁剪）。 */
        val origin: Rect
    )

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
