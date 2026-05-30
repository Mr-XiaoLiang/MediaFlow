package com.lollipop.mediaflow.video

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.source.preload.TargetPreloadStatusControl
import kotlin.math.abs

@UnstableApi
class VideoPreloadStatusControl(
    private val currentPlayingIndex: () -> Int
) : TargetPreloadStatusControl<Int, DefaultPreloadManager.PreloadStatus> {
    override fun getTargetPreloadStatus(rankingData: Int): DefaultPreloadManager.PreloadStatus {
        val currentIndex = currentPlayingIndex()
        if (rankingData - currentIndex == 1) { // next track
            // return a PreloadStatus that is labelled by STAGE_SPECIFIED_RANGE_LOADED and
            // suggest loading 3000ms from the default start position
            return DefaultPreloadManager.PreloadStatus.specifiedRangeLoaded(3000L)
        } else if (rankingData - currentIndex == -1) { // previous track
            // return a PreloadStatus that is labelled by STAGE_SPECIFIED_RANGE_LOADED and
            // suggest loading 3000ms from the default start position
            return DefaultPreloadManager.PreloadStatus.specifiedRangeLoaded(3000L)
        } else if (abs(rankingData - currentIndex) == 2) {
            // return a PreloadStatus that is labelled by STAGE_TRACKS_SELECTED
            return DefaultPreloadManager.PreloadStatus.PRELOAD_STATUS_TRACKS_SELECTED
        } else if (abs(rankingData - currentIndex) <= 4) {
            // return a PreloadStatus that is labelled by STAGE_SOURCE_PREPARED
            return DefaultPreloadManager.PreloadStatus.PRELOAD_STATUS_SOURCE_PREPARED
        }
        return DefaultPreloadManager.PreloadStatus.PRELOAD_STATUS_NOT_PRELOADED
    }
}