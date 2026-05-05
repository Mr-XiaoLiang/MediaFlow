package com.lollipop.auditory.state

import androidx.compose.runtime.mutableIntStateOf

object UIState {

    /**
     * 播放器悬浮高度
     * 单位为px
     * 默认值为0，表示被隐藏了，其实这是不可能的
     */
    val playerPeekHeight = mutableIntStateOf(0)

    /**
     * 正在播放的歌曲的hash值
     */
    val playingHash = mutableIntStateOf(0)

}