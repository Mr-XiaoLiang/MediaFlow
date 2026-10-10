package com.lollipop.mediaflow.page.play

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import androidx.media3.exoplayer.ExoPlayer
import com.lollipop.mediaflow.data.MediaSource
import com.lollipop.mediaflow.data.SourceLoader
import com.lollipop.mediaflow.data.local.ArchiveQuick
import com.lollipop.mediaflow.data.local.LocalState
import com.lollipop.mediaflow.data.local.MediaInfo
import com.lollipop.mediaflow.data.local.MediaType
import com.lollipop.mediaflow.page.flow.compose.VideoFlowComposeScreen
import com.lollipop.mediaflow.playback.FlowPlaybackController
import com.lollipop.mediaflow.tools.ArchiveHelper
import com.lollipop.mediaflow.tools.MediaPlayLauncher
import com.lollipop.mediaflow.ui.BasicFlowComposeActivity
import kotlinx.coroutines.launch

/**
 * 视频播放页（**纯 Compose 新版**，与旧 [VideoFlowActivity] 并存）。
 *
 * ## 数据（里程碑 5）
 * 列表**不再拷贝一份**，而是观察 [`MediaSource`] 的投影结果（其内部即 `MediaView.items` 的
 * Compose State）：归档 / 刷新 / 排序 / 范围变化都会让投影重算，组合层自动重组。
 * 进入页面只做一次 `SourceLoader.fill`（读缓存投影，不扫盘）。
 *
 * ## 归档与索引收敛
 * 归档走唯一写路径 [`ArchiveHelper`] → [`com.lollipop.mediaflow.data.source.MediaBackends`]：
 * 后端更新共享 Catalog，`MediaView` 重算投影 ⇒ 列表变短；本页只需把当前索引夹回有效范围
 * （见 [ContentPanel] 中的 `LaunchedEffect(videos.size)`）。
 *
 * ## 尚未接入（台账 M4-7 ~ M4-9）
 * 倍速长按弹窗、字幕轨道选择弹窗、侧栏、PiP 动作、热键、多来源（`SourceId` 目前固定 Local）。
 * 因此本页**暂不替换**首页入口（切换时机见计划里程碑 9）。
 */
class VideoFlowComposeActivity : BasicFlowComposeActivity() {

    private val mediaParams = MediaPlayLauncher.params()

    /** 当前页索引（供位置回传与标题更新）。 */
    private val currentIndex = mutableIntStateOf(0)

    private val controller by lazy {
        FlowPlaybackController(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaParams.onCreate(this, savedInstanceState)
        // 只读缓存投影（快、不扫盘）；列表的后续变化由 MediaView 的 State 驱动
        lifecycleScope.launch {
            SourceLoader.Local.fill(
                this@VideoFlowComposeActivity,
                LocalState.of(mediaParams.visibility, MediaType.Video)
            )
        }
    }

    override fun onStop() {
        super.onStop()
        // 位置回传（离开页面时把当前索引写回结果，供首页滚动定位）
        mediaParams.onSelected(this, currentIndex.intValue)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mediaParams.onSaveInstanceState(this, outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        controller.release()
    }

    @Composable
    override fun ContentPanel() {
        val source = remember {
            MediaSource.of(mediaParams.visibility, MediaType.Video)
        }
        // 观察式读取：投影 State 在组合中被订阅，Catalog 版本 / 排序 / 范围变化都会触发重组
        val videos = source.local.filterIsInstance<MediaInfo.File>()

        // 归档 / 移除后索引收敛：列表变短时把当前页夹回有效范围
        LaunchedEffect(videos.size) {
            val lastIndex = videos.lastIndex
            currentIndex.intValue = when {
                lastIndex < 0 -> 0
                currentIndex.intValue > lastIndex -> lastIndex
                else -> currentIndex.intValue
            }
        }
        // 标题随当前页变化
        LaunchedEffect(currentIndex.intValue, videos) {
            updateTitleFor(videos, currentIndex.intValue)
        }

        VideoFlowComposeScreen(
            controller = controller,
            items = videos,
            initialIndex = mediaParams.currentPosition,
            isDecorationVisible = shell.isDecorationVisible,
            onChangeDecoration = { visible -> changeDecoration(visible) },
            onIndexChanged = { index ->
                currentIndex.intValue = index
                mediaParams.onSelected(this, index)
            },
            onArchiveClick = { media, quick -> archive(media, quick) },
            onSubtitleClick = { media, player -> onSubtitleClicked(media, player) }
        )
    }

    private fun updateTitleFor(videos: List<MediaInfo.File>, index: Int) {
        val media = videos.getOrNull(index) ?: return
        updateTitle(
            titleValue = media.name,
            dimensions = "",
            size = media.sizeFormat,
            format = media.suffix.uppercase(),
            duration = ""
        )
    }

    /**
     * 归档：写路径唯一（后端更新 Catalog），列表由投影自动收敛，这里不再手工改列表。
     *
     * `ArchiveQuick.Other` 的情况会先弹归档目录选择框（沿用既有 `ArchiveHelper` 行为）。
     */
    private fun archive(media: MediaInfo.File, quick: ArchiveQuick) {
        lifecycleScope.launch {
            ArchiveHelper.remove(
                context = this@VideoFlowComposeActivity,
                file = media,
                quick = quick,
                visibility = mediaParams.visibility
            ) {
                // 无需手动移除：投影重算后列表自然变短；索引收敛由 ContentPanel 的副作用统一处理
            }
        }
    }

    private fun onSubtitleClicked(media: MediaInfo.File, player: ExoPlayer?) {
        // 字幕轨道选择弹窗：待接入（台账 M4-8）
    }
}
