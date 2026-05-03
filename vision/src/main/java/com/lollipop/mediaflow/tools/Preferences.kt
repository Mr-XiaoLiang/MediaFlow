package com.lollipop.mediaflow.tools

import android.content.Context
import com.lollipop.common.tools.PreferencesBasic
import com.lollipop.mediaflow.data.MediaSort

object Preferences : PreferencesBasic() {

    fun init(context: Context) {
        PreferencesDelegate.init(context)
    }

    /**
     * 播放速度的范围
     */
    val playbackSpeedRange = 0.3F..4F

    /**
     * 手势变化进度时的基础倍率范围
     */
    val videoTouchSeekBaseWeightRange = 0.3F..1.2F

    /**
     * 纵向手势范围权重范围
     */
    val videoTouchMaxRangeRatioYRange = 0.1F..1F

    /**
     * 是否开启快速移动到回收站的模式
     */
    val isQuickArchiveEnable by lazy {
        BooleanItem(name = "isQuickArchiveEnable", def = false)
    }

    /**
     * 是否显示其他快速移动到回收站的按钮
     */
    val isShowOtherQuickArchiveButton by lazy {
        BooleanItem(name = "isShowOtherQuickArchiveButton", def = true)
    }

    /**
     * 手指手势变化进度时的基础倍率
     */
    val videoTouchSeekBaseWeight by lazy {
        FloatItem(name = "videoTouchSeekBaseWeight", 0.3F)
    }

    /**
     * 倍速播放时候的速度
     */
    val playbackSpeed by lazy {
        FloatItem(name = "playbackSpeed", 2F)
    }

    /**
     * 纵向手势范围权重
     */
    val videoTouchMaxRangeRatioY by lazy {
        FloatItem(name = "videoTouchMaxRangeRatioY", 0.5F)
    }

    /**
     * 将视频背景渲染为同色的高斯模糊版本
     */
    val isBlurVideoBackground by lazy {
        BooleanItem(name = "isBlurVideoBackground", true)
    }

    /**
     * 是否显示全屏按钮
     */
    val isShowFullscreenBtn by lazy {
        BooleanItem(name = "isShowFullscreenBtn", true)
    }

    /**
     * 是否显示侧边栏按钮
     */
    val isShowSidePanelBtn by lazy {
        BooleanItem(name = "isShowSidePanelBtn", true)
    }

    /**
     * 是否显示抽屉按钮
     */
    val isShowDrawerBtn by lazy {
        BooleanItem(name = "isShowDrawerBtn", true)
    }

    /**
     * 是否显示返回按钮
     */
    val isShowBackBtn by lazy {
        BooleanItem(name = "isShowBackBtn", true)
    }

    /**
     * 是否显示标题栏
     */
    val isShowTitle by lazy {
        BooleanItem(name = "isShowTitle", true)
    }

    /**
     * 是否显示标签栏
     */
    val isShowTag by lazy {
        BooleanItem(name = "isShowTag", true)
    }

    /**
     * 是否开启侧边栏手势
     */
    val isSidePanelGestureEnable by lazy {
        BooleanItem(name = "isSidePanelGestureEnable", false)
    }

    /**
     * 公开视频排序
     */
    val publicVideoSort by lazy {
        MediaSortItem(name = "publicVideoSort", MediaSort.DateDesc)
    }

    /**
     * 公开图片排序
     */
    val publicPhotoSort by lazy {
        MediaSortItem(name = "publicPhotoSort", MediaSort.DateDesc)
    }

    /**
     * 私有视频排序
     */
    val privateVideoSort by lazy {
        MediaSortItem(name = "privateVideoSort", MediaSort.DateDesc)
    }

    /**
     * 私有图片排序
     */
    val privatePhotoSort by lazy {
        MediaSortItem(name = "privatePhotoSort", MediaSort.DateDesc)
    }

    class MediaSortItem(
        val name: String,
        val def: MediaSort
    ) : TypedItem<MediaSort>() {
        override fun getPreferencesValue(): MediaSort {
            val key = PreferencesDelegate.get(name = name, def = def.key)
            return MediaSort.findByKey(key) ?: def
        }

        override fun setPreferencesValue(value: MediaSort) {
            PreferencesDelegate.set(name = name, value = value.key)
        }

    }

}