package com.lollipop.mediaflow.page.play

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import com.lollipop.mediaflow.data.MediaSource
import com.lollipop.mediaflow.data.SourceLoader
import com.lollipop.mediaflow.data.local.ArchiveQuick
import com.lollipop.mediaflow.data.local.LocalState
import com.lollipop.mediaflow.data.local.MediaInfo
import com.lollipop.mediaflow.data.local.MediaType
import com.lollipop.mediaflow.page.flow.compose.FlowBlurBackground
import com.lollipop.mediaflow.page.flow.compose.FlowSidePanel
import com.lollipop.mediaflow.page.flow.compose.VideoFlowComposeScreen
import com.lollipop.mediaflow.playback.FlowHotKeyTarget
import com.lollipop.mediaflow.playback.FlowPipActions
import com.lollipop.mediaflow.playback.FlowPlaybackController
import com.lollipop.mediaflow.tools.ArchiveHelper
import com.lollipop.mediaflow.tools.FlowHotKeyDelegate
import com.lollipop.mediaflow.tools.MediaPlayLauncher
import com.lollipop.mediaflow.tools.PIPHelper
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.mediaflow.ui.BasicFlowComposeActivity
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 视频播放页（**纯 Compose 新版**，与旧 [VideoFlowActivity] 并存）。
 *
 * ## 数据（里程碑 5）
 * 列表不拷贝，直接观察 [`MediaSource`] 的投影（其内部即 `MediaView.items` 的 State）：
 * 归档 / 刷新 / 排序 / 范围变化都会重投影并自动重组；进入页面只做一次 `SourceLoader.fill`。
 *
 * ## 归档与索引收敛
 * 归档走唯一写路径（`ArchiveHelper` → `MediaBackends`），列表由投影自然变短；
 * 本页只把当前索引夹回有效范围。
 *
 * ## 画中画 / 热键
 * 两者都通过「中转对象」与页面解耦：
 * - [`FlowPipActions`]：页面在组合期写入动作与状态，本页据此构建 `PIPHelper` 参数；
 * - [`FlowHotKeyTarget`]：页面把「当前页 player + 翻页动作」绑上去，[`FlowHotKeyDelegate`] 只管键位语义。
 *
 * ## 入口（里程碑 9）
 * 首页视频入口已指向本页（见 `MediaPlayLauncher` 与 manifest）；旧 `VideoFlowActivity`
 * 仅保留代码，待人工验证通过后随旧表现层一并删除。
 *
 * ## 仍待接入
 * 多来源（`SourceId` 目前固定 Local）。
 */
class VideoFlowComposeActivity : BasicFlowComposeActivity() {

    private val mediaParams = MediaPlayLauncher.params()

    /** 当前页索引（供位置回传与标题更新）。 */
    private val currentIndex = mutableIntStateOf(0)

    /**
     * 背景层的底图来源（`FlowBackground` 插槽的内容）。
     *
     * **默认不更新**：平时它被内容区（Pager 各页）严丝合缝地盖住，无需与当前页同步；
     * 只在侧栏过渡开始时抓一次当前项，让内容区淡出后露出的是这张图。
     */
    private var backdropMedia by mutableStateOf<MediaInfo.File?>(null)

    private val controller by lazy {
        FlowPlaybackController(this)
    }

    private val pipActions = FlowPipActions()

    private val hotKeyTarget = FlowHotKeyTarget()

    /** 侧栏点击产生的跳转请求（[NoScrollRequest] 表示无）。 */
    private val scrollRequest = mutableIntStateOf(NoScrollRequest)

    private val pipHolder = PIPHelper.registerPipActions(this) { action ->
        when (action) {
            PIPHelper.Action.PLAY -> pipActions.onPlay()
            PIPHelper.Action.PAUSE -> pipActions.onPause()
            PIPHelper.Action.PREVIOUS -> pipActions.onPrevious()
            PIPHelper.Action.NEXT -> pipActions.onNext()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaParams.onCreate(this, savedInstanceState)
        // 触发 PiP 广播注册（Holder 内部随生命周期注册 / 注销）
        pipHolder
        registerHotKey()
        observePipState()
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

    override fun onPictureInPictureRequested(): Boolean {
        return pipHolder.onPictureInPictureRequested()
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
        // 过渡开始时抓一次当前项交给背景层（内容区的淡出 / 让位由外壳负责）
        LaunchedEffect(shell.isContentSuppressed) {
            if (shell.isContentSuppressed) {
                backdropMedia = videos.getOrNull(currentIndex.intValue)
            }
        }

        VideoFlowComposeScreen(
            controller = controller,
            items = videos,
            initialIndex = mediaParams.currentPosition,
            isDecorationVisible = shell.isDecorationVisible,
            // 侧栏开关过渡期间抑制内容（本页据此暂停播放）
            isContentSuppressed = shell.isContentSuppressed,
            pipActions = pipActions,
            hotKeyTarget = hotKeyTarget,
            scrollToIndex = scrollRequest.intValue,
            onScrollRequestHandled = { scrollRequest.intValue = NoScrollRequest },
            onChangeDecoration = { visible -> changeDecoration(visible) },
            onIndexChanged = { index ->
                currentIndex.intValue = index
                mediaParams.onSelected(this, index)
                updatePipParams()
            },
            onArchiveClick = { media, quick -> archive(media, quick) }
        )
    }

    /**
     * 侧栏内容（`FlowScaffold` 的侧栏槽位）。
     *
     * 与主内容区读**同一个** `MediaView`（`MediaSource.of` 单例），因此列表天然同步；
     * 选中项取自 [currentIndex]，点击只发起跳转请求，由 [VideoFlowComposeScreen] 消费后滚动。
     */
    /**
     * 背景层（外壳的第 1 个容器）：黑底 + 当前视频的模糊图。
     *
     * 与页内模糊背景**同款实现**（相同目标尺寸 / 模糊半径 / 压暗色），因此几乎必然命中
     * Coil 的内存缓存——既不用重新解码，也不必导出 Bitmap。黑底保证资源未就绪时是黑场，
     * 模糊图只在内容区淡出期间露出。
     */
    @Composable
    override fun FlowBackground() {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            backdropMedia?.let { media ->
                FlowBlurBackground(media = media, modifier = Modifier.fillMaxSize())
            }
        }
    }

    @Composable
    override fun SidePanel() {
        val source = remember {
            MediaSource.of(mediaParams.visibility, MediaType.Video)
        }
        FlowSidePanel(
            items = source.local.filterIsInstance<MediaInfo.File>(),
            selectedIndex = currentIndex.intValue,
            onItemClick = { index -> scrollRequest.intValue = index }
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

    /** 热键：仅在偏好开启时注册（对齐旧 `registerHotKey` 的门控）。 */
    private fun registerHotKey() {
        if (!Preferences.isHotKeyEnable.get()) {
            return
        }
        FlowHotKeyDelegate.register(window = window, target = hotKeyTarget)
    }

    /**
     * 观察 PiP 需要的状态（播放中 / 是否首尾 / 视频尺寸），变化时刷新 `PIPHelper` 参数。
     *
     * 旧实现是在播放回调里主动调用 `updatePipParams()`；新链路的状态由页面写入 [FlowPipActions]，
     * 因此这里用 `snapshotFlow` 被动跟随，避免页面与 Activity 相互调用。
     */
    private fun observePipState() {
        lifecycleScope.launch {
            snapshotFlow {
                PipState(
                    isPlaying = pipActions.isPlaying,
                    hasPrevious = pipActions.hasPrevious,
                    hasNext = pipActions.hasNext,
                    videoWidth = pipActions.videoWidth,
                    videoHeight = pipActions.videoHeight
                )
            }
                .distinctUntilChanged()
                .collect {
                    updatePipParams()
                }
        }
    }

    private fun updatePipParams() {
        val option = PIPHelper.Option(
            hasPrev = pipActions.hasPrevious,
            hasNext = pipActions.hasNext,
            hasPlay = !pipActions.isPlaying,
            hasPause = pipActions.isPlaying
        )
        val width = pipActions.videoWidth
        val height = pipActions.videoHeight
        if (width > 0 && height > 0) {
            pipHolder.setParams(width, height, option)
        } else {
            pipHolder.setParams(NominalPipWidth, NominalPipHeight, option)
        }
    }

    /** PiP 参数刷新用的状态快照（`distinctUntilChanged` 需要值语义）。 */
    private data class PipState(
        val isPlaying: Boolean,
        val hasPrevious: Boolean,
        val hasNext: Boolean,
        val videoWidth: Int,
        val videoHeight: Int
    )

    private companion object {

        /** 无跳转请求。 */
        const val NoScrollRequest = -1

        /** 视频尺寸未知时的占位比例（与旧实现缺失 metadata 时的默认一致）。 */
        const val NominalPipWidth = 100
        const val NominalPipHeight = 100
    }
}
