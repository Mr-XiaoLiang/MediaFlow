package com.lollipop.mediaflow.video

interface VideoController {

     fun seekTo(ms: Long)

     fun pause()

     fun isPlaying(): Boolean

     fun play()

     fun startPlaybackSpeed()

     fun stopPlaybackSpeed()

     fun startSeekMode()

     fun onTouchSeek(weight: Float, precision: Float)

     fun stopSeekMode(weight: Float)

     fun selectTrack(track: VideoTrack?)

}