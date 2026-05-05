package com.lollipop.auditory.state

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.util.Size
import androidx.palette.graphics.Palette
import com.lollipop.auditory.data.AudioInfo
import com.lollipop.auditory.ui.DefaultDarkColorScheme
import com.lollipop.auditory.ui.MonetColor
import com.lollipop.common.tools.LLog.Companion.registerLog
import com.lollipop.common.tools.doAsync
import com.lollipop.common.tools.onUI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object UIStateRepository {

    private val log by lazy {
        registerLog()
    }

    private val playingInfoFlow = MutableStateFlow(AudioInfo.EMPTY)
    private val darkModeFlow = MutableStateFlow(false)
    private val playingThemeFlow = MutableStateFlow(DefaultDarkColorScheme)
    private var playingPalette: Palette? = null

    private var audioChangeCount = 0L

    /**
     * 正在播放的歌曲的信息
     */
    val playingInfo = playingInfoFlow.asStateFlow()

    /**
     * 是否暗色模式
     */
    val isDarkMode = darkModeFlow.asStateFlow()

    /**
     * 正在播放的歌曲的主题色
     */
    val playingTheme = playingThemeFlow.asStateFlow()

    private fun onAudioChange(): Long {
        audioChangeCount++
        return audioChangeCount
    }

    private fun isCurrentAudio(audioChangeCount: Long): Boolean {
        return audioChangeCount == this.audioChangeCount
    }

    /**
     * 暗色模式变化时，更新 UI 状态
     */
    fun onDarkModeChanged(isDarkMode: Boolean) {
        val oldState = darkModeFlow.value
        if (oldState == isDarkMode) {
            return
        }
        darkModeFlow.update { isDarkMode }
        updateTheme()
    }

    /**
     * 正在播放的歌曲信息变化时，更新 UI 状态
     */
    fun onPlayingInfoChanged(context: Context, audioInfo: AudioInfo) {
        playingInfoFlow.update { audioInfo }
        playingPalette = null
        updateTheme()
        val currentAudioCount = onAudioChange()
        doAsync {
            val uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                audioInfo.id
            )
            try {
                // 指定需要的尺寸
                val size = Size(512, 512)
                val bitmap = context.contentResolver.loadThumbnail(uri, size, null)
                // 成功加载，可以开始计算 Palette 了
                // TODO 这里的封面应该可以保存起来的，还没做
                val palette = Palette.from(bitmap).generate()
                onUI {
                    if (isCurrentAudio(currentAudioCount)) {
                        playingPalette = palette
                        updateTheme()
                    }
                }
            } catch (e: Throwable) {
                // 说明该音频没有内置封面
                log.e("onPlayingInfoChanged.loadThumbnail", e)
            }
        }
    }

    private fun updateTheme() {
        val palette = playingPalette
        val titleHash = playingInfo.value.titleHash
        val isDark = isDarkMode.value
        playingThemeFlow.value = MonetColor.createTheme(palette, titleHash, isDark)
    }

}