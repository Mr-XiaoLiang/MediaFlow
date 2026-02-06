package com.lollipop.mediaflow.video

import androidx.media3.common.Player

class VideoEventObserver {

    val playerListener: Player.Listener = PlayerListenerWrapper(this)

    var focusListener: VideoListener? = null

    private val listenerManager = ArrayList<VideoListener>()

    fun addListener(listener: VideoListener) {
        listenerManager.add(listener)
    }

    fun removeListener(listener: VideoListener) {
        listenerManager.remove(listener)
    }

    fun setFocusListener(listener: VideoListener?) {
        focusListener = listener
    }

    private fun invoke(block: VideoListener.() -> Unit) {
        focusListener?.block()
        for (listener in listenerManager) {
            listener.block()
        }
    }

    private class PlayerListenerWrapper(
        private val observer: VideoEventObserver
    ) : Player.Listener {

    }

}