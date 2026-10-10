package com.lollipop.mediaflow.page.play

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import com.lollipop.mediaflow.data.local.LocalMediaLoader
import com.lollipop.mediaflow.data.local.MediaInfo
import com.lollipop.mediaflow.data.local.MetadataLoader
import com.lollipop.mediaflow.page.flow.compose.VideoFlowComposeScreen
import com.lollipop.mediaflow.playback.FlowPipActions
import com.lollipop.mediaflow.playback.FlowPlaybackController
import com.lollipop.mediaflow.tools.PIPHelper
import com.lollipop.mediaflow.ui.BasicFlowComposeActivity
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 快捷播放页（**纯 Compose 新版**，与旧 [VideoQuickPlayActivity] 并存）。
 *
 * 复用与视频页**完全相同**的组件（[VideoFlowComposeScreen] + 播放器池 + 手势 + 控件层 + 字幕），
 * 差异只在「数据来源与开关」：
 * - 数据来自外部 `VIEW` intent 的单个视频（`LocalMediaLoader.loadMediaFileSync`），不是库投影；
 * - 单曲循环（`REPEAT_MODE_ONE`，对齐旧实现）；
 * - 归档按钮禁用（`isArchiveEnabled = false`，对齐旧 `videoHolder.archiveEnable = false`）；
 * - 不注册热键、不提供侧栏（旧实现同样没有）。
 *
 * 入口（里程碑 9）：`VIEW` intent-filter 已由本页承接（旧 [VideoQuickPlayActivity] 已改为
 * `exported=false`）；旧页仅保留代码，待人工验证通过后随旧表现层一并删除。
 */
class VideoQuickPlayComposeActivity : BasicFlowComposeActivity() {

    private val controller by lazy {
        FlowPlaybackController(this)
    }

    /** 单元素列表：与参数化组件的数据契约一致（列表 + 索引）。 */
    private val videos = mutableStateListOf<MediaInfo.File>()

    private val pipActions = FlowPipActions()

    private val pipHolder = PIPHelper.registerPipActions(this) { action ->
        when (action) {
            PIPHelper.Action.PLAY -> pipActions.onPlay()
            PIPHelper.Action.PAUSE -> pipActions.onPause()
            // 单曲场景没有上一项 / 下一项（页面会把 hasPrevious / hasNext 置为 false，按钮不会出现）
            PIPHelper.Action.PREVIOUS -> Unit
            PIPHelper.Action.NEXT -> Unit
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 单曲循环（对齐旧实现）
        controller.setRepeatModeAll(Player.REPEAT_MODE_ONE)
        pipHolder
        observePipState()
        val videoUri = intent.data
        if (videoUri != null) {
            lifecycleScope.launch {
                LocalMediaLoader.loadMediaFileSync(this@VideoQuickPlayComposeActivity, videoUri)
                    ?.let { media ->
                        videos.clear()
                        videos.add(media)
                        updateTitleFor(media)
                    }
            }
        }
    }

    override fun onPictureInPictureRequested(): Boolean {
        return pipHolder.onPictureInPictureRequested()
    }

    @Composable
    override fun ContentPanel() {
        VideoFlowComposeScreen(
            controller = controller,
            items = videos,
            initialIndex = 0,
            isDecorationVisible = shell.isDecorationVisible,
            isArchiveEnabled = false,
            pipActions = pipActions,
            onChangeDecoration = { visible -> changeDecoration(visible) }
        )
    }

    /** 标题 + 标签：与旧实现一样按需读取元数据补全尺寸 / 时长。 */
    private fun updateTitleFor(media: MediaInfo.File) {
        updateTitle(
            titleValue = media.name,
            dimensions = "",
            size = media.sizeFormat,
            format = media.suffix.uppercase(),
            duration = ""
        )
        MetadataLoader.load(this, media) { metadata ->
            updateTitle(
                titleValue = media.name,
                dimensions = metadata?.dimensionsFormat ?: "",
                size = media.sizeFormat,
                format = media.suffix.uppercase(),
                duration = metadata?.durationFormat ?: ""
            )
        }
    }

    /** PiP 参数跟随页面状态刷新（与视频页同一策略）。 */
    private fun observePipState() {
        lifecycleScope.launch {
            snapshotFlow {
                PipState(
                    isPlaying = pipActions.isPlaying,
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
            hasPrev = false,
            hasNext = false,
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

    private data class PipState(
        val isPlaying: Boolean,
        val videoWidth: Int,
        val videoHeight: Int
    )

    private companion object {

        /** 视频尺寸未知时的占位比例（与视频页一致）。 */
        const val NominalPipWidth = 100
        const val NominalPipHeight = 100
    }
}
