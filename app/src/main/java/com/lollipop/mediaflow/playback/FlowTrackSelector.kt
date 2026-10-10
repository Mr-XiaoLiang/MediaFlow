package com.lollipop.mediaflow.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import com.lollipop.mediaflow.video.VideoTrack

/**
 * 字幕轨道选择（从旧 `VideoManager.selectTrack` 抽出的**单一来源**）。
 *
 * - `null`：关闭字幕（禁用 `TRACK_TYPE_TEXT`）；
 * - 非 null：启用字幕并覆盖到指定 `(group, index)`。
 *
 * 抽出目的：新旧两套表现层共用同一份轨道选择语义；后续旧 `VideoManager` 也可改为调用本函数。
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
