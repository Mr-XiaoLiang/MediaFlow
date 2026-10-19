package com.lollipop.mediaflow.playback

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.lollipop.mediaflow.video.VideoTrack
import com.lollipop.mediaflow.video.VideoTrackGroup

/**
 * 字幕轨道语义（**单一来源**）。
 *
 * 本文件承接两件事：
 * 1. [applySubtitleSelection]：字幕开关 / 覆盖轨道（从旧 `VideoManager.selectTrack` 抽出）；
 * 2. [findSubtitleTracks]：从 [Tracks] 中筛出字幕轨道（从旧 `VideoManager.findTrack` 迁移而来）。
 *
 * 抽出目的：新旧两套表现层共用同一份字幕语义；旧 `VideoManager` 删除后，本文件即唯一实现。
 */

/**
 * 应用字幕选择。
 *
 * - `null`：关闭字幕（禁用 `TRACK_TYPE_TEXT`）；
 * - 非 null：启用字幕并覆盖到指定 `(group, index)`。
 */
@OptIn(UnstableApi::class)
fun Player.applySubtitleSelection(track: VideoTrack?) {
    val builder = trackSelectionParameters.buildUpon()
    if (track == null) {
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
    } else {
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        builder.setOverrideForType(TrackSelectionOverride(track.group, track.index))
    }
    trackSelectionParameters = builder.build()
}

/**
 * 从 [tracks] 中筛出 `TRACK_TYPE_TEXT` 类型的字幕轨道。
 *
 * 行为与旧 `VideoManager.findTrack` 保持一致：单个轨道元数据解析异常时整体吞掉，
 * 返回已收集到的部分结果（旧实现即如此，避免因个别畸形轨道导致整页报错）。
 */
@OptIn(UnstableApi::class)
fun findSubtitleTracks(tracks: Tracks): VideoTrackGroup {
    val result = mutableListOf<VideoTrack>()
    var enable = false
    runCatching {
        tracks.groups.forEach { trackGroup ->
            if (trackGroup.type != C.TRACK_TYPE_TEXT) {
                return@forEach
            }
            for (i in 0 until trackGroup.length) {
                val format = trackGroup.getTrackFormat(i)
                val isSelected = trackGroup.isTrackSelected(i)
                result.add(
                    VideoTrack(
                        group = trackGroup.mediaTrackGroup,
                        index = i,
                        label = format.label ?: "",       // 字幕名称，如 "中文"、"English"
                        language = format.language ?: "", // 语言代码，如 "zh"、"en"
                        isSelected = isSelected
                    )
                )
                enable = enable || isSelected
            }
        }
    }.onFailure {
        Log.e(TAG, "findSubtitleTracks failed", it)
    }
    return VideoTrackGroup(enable = enable, tracks = result)
}

private const val TAG = "FlowTrackSelector"
