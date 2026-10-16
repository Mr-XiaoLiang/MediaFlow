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
import com.lollipop.mediaflow.data.MediaMetadata
import com.lollipop.mediaflow.data.MediaSource
import com.lollipop.mediaflow.data.SourceLoader
import com.lollipop.mediaflow.data.local.ArchiveQuick
import com.lollipop.mediaflow.data.local.LocalState
import com.lollipop.mediaflow.data.LMedia
import com.lollipop.mediaflow.data.local.MediaType
import com.lollipop.mediaflow.data.sizeFormat
import com.lollipop.mediaflow.data.suffix
import com.lollipop.mediaflow.page.flow.FlowGuide
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
    private var backdropMedia by mutableStateOf<LMedia?>(null)

    /** 当前项的元数据：用于 PiP 窗口比例（宽高 + needRotate 交换），对齐旧实现的取值来源。 */
    private var pipMetadata by mutableStateOf<MediaMetadata?>(null)

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
        // 先落一次参数（元数据未就绪时 [PIPHelper] 用 100×100 占位），
        // 否则 Holder 的 lastParams 为 null，进入 PiP 会被拒绝
        updatePipParams()
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
        val videos = source.bySource(mediaParams.params.sourceId)

        // 归档 / 移除后索引收敛：列表变短时把当前页夹回有效范围
        LaunchedEffect(videos.size) {
            val lastIndex = videos.lastIndex
            if (lastIndex < 0) {
                // 集合为空 → 关闭页面（Pager 给不了，必须由我们做）
                finish()
                return@LaunchedEffect
            }
            if (currentIndex.intValue > lastIndex) {
                currentIndex.intValue = lastIndex
            }
        }
        // 标题随当前页变化；同时刷新 PiP 比例所需的元数据
        LaunchedEffect(currentIndex.intValue, videos) {
            updateTitleFor(videos, currentIndex.intValue)
            videos.getOrNull(currentIndex.intValue)?.let { updatePipMetadata(it) }
        }
        // 过渡开始时抓一次当前项交给背景层（内容区的淡出 / 让位由外壳负责）
        LaunchedEffect(shell.isContentSuppressed) {
            if (shell.isContentSuppressed) {
                backdropMedia = videos.getOrNull(currentIndex.intValue)
            }
        }

        // 播放页引导：手势区在画面上没有任何提示，首次进入时说明一次
        FlowGuide {
            VideoFlowComposeScreen(
                controller = controller,
                items = videos,
                initialIndex = mediaParams.currentPosition,
                isDecorationVisible = shell.isDecorationVisible,
                // 画中画模式下隐藏控件层（对齐旧 controllerVisibleFilter.onPipChanged）
                isPipMode = shell.isPipMode,
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
     * 内存缓存——既不用重新解码，也不必导出 Bitmap。黑底保证资源未就绪时是黑场，
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
            items = source.bySource(mediaParams.params.sourceId),
            selectedIndex = currentIndex.intValue,
            onItemClick = { index -> scrollRequest.intValue = index }
        )
    }

    /** 最近一次请求补全标签的媒体 id：异步回调回来时若已不是它，就丢弃（快速翻页保护）。 */
    private var pendingTitleMediaId: String? = null

    /**
     * 标题 + 标签：与旧实现 [`VideoFlowActivity.onSelected`](VideoFlowActivity.kt) 一致，**分两段**——
     *
     * 1. 先给标题与立即可得的标签（大小 / 格式）；
     * 2. 再用元数据补上**分辨率与时长**（`MediaMetadata.dimensionsFormat` / `durationFormat`）。
     *
     * 之前只做了第 1 段、且把 `dimensions` 与 `duration` 写死为空，因此标题栏比旧版少两个标签。
     * 第 2 段是异步的，所以要校验「回调回来时仍是当前这一项」，否则快速翻页会把上一项的
     * 分辨率 / 时长套到当前项上。
     */
    private fun updateTitleFor(videos: List<LMedia>, index: Int) {
        val media = videos.getOrNull(index) ?: return
        updateTitle(
            titleValue = media.name,
            dimensions = "",
            size = media.sizeFormat,
            format = media.suffix.uppercase(),
            duration = ""
        )
        pendingTitleMediaId = media.mediaId
        media.loadMetadata(this) { metadata ->
            if (pendingTitleMediaId != media.mediaId) {
                return@loadMetadata
            }
            updateTitle(
                titleValue = media.name,
                dimensions = metadata?.dimensionsFormat ?: "",
                size = media.sizeFormat,
                format = media.suffix.uppercase(),
                duration = metadata?.durationFormat ?: ""
            )
        }
    }

    /**
     * 归档：写路径唯一（后端更新 Catalog），列表由投影自动收敛，这里不再手工改列表。
     *
     * `ArchiveQuick.Other` 的情况会先弹归档目录选择框（沿用既有 `ArchiveHelper` 行为）。
     */
    private fun archive(media: LMedia, quick: ArchiveQuick) {
        lifecycleScope.launch {
            ArchiveHelper.remove(
                context = this@VideoFlowComposeActivity,
                item = media,
                sourceId = mediaParams.params.sourceId,
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
                    hasNext = pipActions.hasNext
                )
            }
                .distinctUntilChanged()
                .collect {
                    updatePipParams()
                }
        }
    }

    /**
     * 刷新 PiP 参数。
     *
     * 比例取自**元数据**（[MediaMetadata] 的宽高，`needRotate` 时由 [PIPHelper] 交换），
     * 与旧实现 `MetadataLoader.load { pipHolder.setParams(it, option) }` 同源；
     * 元数据未就绪时退回 100×100 占位。动作显隐由 [FlowPipActions] 的状态决定。
     */
    private fun updatePipParams() {
        val option = PIPHelper.Option(
            hasPrev = pipActions.hasPrevious,
            hasNext = pipActions.hasNext,
            hasPlay = !pipActions.isPlaying,
            hasPause = pipActions.isPlaying
        )
        pipHolder.setParams(pipMetadata, option)
    }

    /** 读取当前项元数据，回调（主线程）后刷新 PiP 比例。 */
    private fun updatePipMetadata(media: LMedia) {
        media.loadMetadata(this) { metadata ->
            pipMetadata = metadata
            updatePipParams()
        }
    }

    /** PiP 参数刷新用的状态快照（`distinctUntilChanged` 需要值语义）。 */
    private data class PipState(
        val isPlaying: Boolean,
        val hasPrevious: Boolean,
        val hasNext: Boolean
    )

    private companion object {

        /** 无跳转请求。 */
        const val NoScrollRequest = -1
    }
}
