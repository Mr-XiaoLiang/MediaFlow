package com.lollipop.mediaflow.playback

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 画中画（PiP）动作与状态的中转站。
 *
 * 分工：
 * - **页面**（[com.lollipop.mediaflow.page.flow.compose.VideoFlowComposeScreen]）在组合期填充
 *   动作回调与状态（当前页 player 的播放状态、是否有上/下一项、视频宽高）；
 * - **Activity** 在收到 [`com.lollipop.mediaflow.tools.PIPHelper`] 的广播（播放 / 暂停 / 上一下 / 下一下）
 *   或需要在 `onPictureInPictureModeChanged` 更新参数时，读取本对象。
 *
 * 之所以做成普通对象而不是 State 传递：PiP 广播来自 `BroadcastReceiver`，不在组合作用域内，
 * 需要一处稳定的引用。
 */
@Stable
class FlowPipActions {

    // ---- 动作（由页面写入） ----

    var onPlay: () -> Unit = {}

    var onPause: () -> Unit = {}

    var onPrevious: () -> Unit = {}

    var onNext: () -> Unit = {}

    // ---- 状态（由页面写入，供 PiP 参数构建） ----

    var isPlaying by mutableStateOf(false)

    var hasPrevious by mutableStateOf(false)

    var hasNext by mutableStateOf(false)

    /** 当前视频宽（用于 PiP 宽高比）。 */
    var videoWidth by mutableIntStateOf(0)

    /** 当前视频高（用于 PiP 宽高比）。 */
    var videoHeight by mutableIntStateOf(0)
}
