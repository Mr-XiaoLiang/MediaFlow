package com.lollipop.auditory.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lollipop.auditory.data.AudioInfo
import com.lollipop.auditory.state.UIStateRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ThemeViewModel : ViewModel() {

    val playingInfo: StateFlow<AudioInfo?> = UIStateRepository.playingInfo
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000), // 离线 5 秒后停止订阅节省资源
            initialValue = null
        )

    /**
     * 播放器悬浮高度
     * 单位为px
     * 默认值为0，表示被隐藏了，其实这是不可能的
     */
    var playerPeekHeight by mutableIntStateOf(0)

}