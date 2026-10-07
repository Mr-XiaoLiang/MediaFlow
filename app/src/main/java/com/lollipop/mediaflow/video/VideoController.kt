package com.lollipop.mediaflow.video

interface VideoController {

     fun seekTo(ms: Long)

     fun pause()

     fun isPlaying(): Boolean

     fun play()

     fun startPlaybackSpeed()

     fun stopPlaybackSpeed()

     fun setPlaybackSpeed(speed: Float)

     fun getPlaybackSpeed(): Float

     fun startSeekMode()

     fun onTouchSeek(weight: Float, speed: Float)

     fun stopSeekMode(weight: Float)

     fun selectTrack(track: VideoTrack?)
    fun seekOffset(offset: Int)

    fun getVideoDuration(): Long

}