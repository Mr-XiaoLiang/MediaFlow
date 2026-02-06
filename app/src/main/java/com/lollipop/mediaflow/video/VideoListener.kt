package com.lollipop.mediaflow.video

interface VideoListener {

    fun onVideoBegin(length: Long)

    fun onVideoProgress(ms: Long)

    fun onPlay()

    fun onPause()

    fun onVideoEnd()

    fun onPlayerError(msg: String)

}