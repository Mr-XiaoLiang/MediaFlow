package com.lollipop.mediaflow.playback

import com.lollipop.mediaflow.data.LMedia

/**
 * 播放进度记忆的策略层（存储见 [PlaybackProgressStore]）。
 *
 * 与存储解耦：进度值存在进程内的 LRU 表中，以 [LMedia.uri] 为键；本文件只承载「何时该记」的
 * 播放策略，不感知来源类型，因此 Local / WebDAV / 未来来源共用同一份规则。
 */

/**
 * 按「剩余 > 1s 才记」的规则更新进度。
 *
 * 口径对齐旧 [`com.lollipop.mediaflow.page.play.VideoFlowActivity.rememberVideoProgress`]：
 * 剩余不足 1 秒时不记（清零），避免"差一点点看完"被记住、下次进入从末尾附近开始。
 *
 * @param positionMs 当前播放位置（毫秒）。
 * @param durationMs 视频总时长（毫秒）；未知（<= 0）时按「不记」处理。
 */
fun LMedia.updatePlaybackProgress(positionMs: Long, durationMs: Long) {
    val remaining = durationMs - positionMs
    val valid = positionMs > 0L && remaining > PlaybackProgressRemainLimitMs
    PlaybackProgressStore.set(uri, if (valid) positionMs else 0L)
}

/** 进度记忆的最小剩余时长（毫秒）：对齐旧实现的 `1000L`。 */
private const val PlaybackProgressRemainLimitMs = 1000L
