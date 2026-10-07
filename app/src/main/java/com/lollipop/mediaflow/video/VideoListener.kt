package com.lollipop.mediaflow.video

import androidx.media3.common.PlaybackParameters

interface VideoListener {

    fun onVideoBegin() {}

    fun onVideoProgress(ms: Long) {}

    fun onPlay() {}

    fun onPause() {}

    fun onVideoEnd() {}

    fun onPlayerError(msg: String) {}

    fun onTracksChanged(tracks: VideoTrackGroup) {}

    fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {}

}