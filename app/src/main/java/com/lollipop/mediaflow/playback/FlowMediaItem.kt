package com.lollipop.mediaflow.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.lollipop.mediaflow.data.local.MediaInfo

/**
 * 数据层 → 播放层的适配：把 [MediaInfo.File] 转成 [MediaItem]。
 *
 * 与旧实现 `VideoPreload.createMediaItem` 保持同一口径：
 * - 主 URI 取 `file.uri`；
 * - 字幕作为 [MediaItem.SubtitleConfiguration] 挂上（mimeType 为空则跳过该项）；
 * - 字幕默认选中（`SELECTION_FLAG_DEFAULT`），是否展示由页面 / 播放器轨道控制决定。
 *
 * 逻辑组件单一来源：新旧两套表现层都复用这一份转换。
 */
@OptIn(UnstableApi::class)
fun MediaInfo.File.toFlowMediaItem(): MediaItem {
    val builder = MediaItem.Builder().setUri(uri)
    val subtitles = ArrayList<MediaItem.SubtitleConfiguration>(subtitleList.size)
    subtitleList.forEach { subtitle ->
        val mimeType = subtitle.mimeType ?: return@forEach
        subtitles.add(
            MediaItem.SubtitleConfiguration.Builder(subtitle.uri)
                .setMimeType(mimeType)
                .setLanguage(subtitle.language)
                .setLabel(subtitle.name)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
        )
    }
    if (subtitles.isNotEmpty()) {
        builder.setSubtitleConfigurations(subtitles)
    }
    return builder.build()
}
