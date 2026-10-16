package com.lollipop.mediaflow.tools

import android.content.Context
import android.content.pm.ActivityInfo
import com.lollipop.mediaflow.data.common.MediaSort
import com.lollipop.mediaflow.ui.effect.Level3
import com.lollipop.mediaflow.ui.effect.PerformanceLevel
import com.lollipop.mediaflow.ui.effect.VisualEffectCapability
import com.lollipop.mediaflow.ui.effect.VisualEffectMode

object Preferences : PreferencesBasic() {

    fun init(context: Context) {
        PreferencesDelegate.init(context)
    }

    /**
     * 播放速度的范围
     */
    val playbackSpeedRange = 0.2F..4F

    /**
     * 手势变化进度时的基础倍率范围
     */
    val videoTouchSeekBaseWeightRange = 0.3F..1.2F

    /**
     * 纵向手势范围权重范围
     */
    val videoTouchMaxRangeRatioYRange = 0.1F..1F

    /**
     * 快速前后跳转的范围
     */
    val videoQuickForwardRange = 5F..45F

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
     * 是否显示旋转按钮
     */
    val isShowRotateBtn by lazy {
        BooleanItem(name = "isShowRotateBtn", true)
    }

    /**
     * 是否显示倍速按钮
     */
    val isShowSpeedBtn by lazy {
        BooleanItem(name = "isShowSpeedBtn", true)
    }

    /**
     * 上一次旋转的模式
     */
    val lastRotateMode by lazy {
        IntItem(name = "lastRotateMode", ActivityInfo.SCREEN_ORIENTATION_FULL_USER)
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
     * 是否开启画中画模式
     */
    val isPictureInPictureEnable by lazy {
        BooleanItem(name = "isPictureInPictureEnable", true)
    }

    /**
     * 画中画模式下是否开启上一曲按钮
     */
    val isPipPrevEnable by lazy {
        BooleanItem(name = "isPipPrevEnable", true)
    }

    /**
     * 画中画模式下是否开启下一曲按钮
     */
    val isPipNextEnable by lazy {
        BooleanItem(name = "isPipNextEnable", true)
    }

    /**
     * 画中画模式下是否开启播放按钮
     */
    val isPipPlayEnable by lazy {
        BooleanItem(name = "isPipPlayEnable", true)
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

    /**
     * 在列表中是否显示标签
     */
    val isDisplayLabelInList by lazy {
        BooleanItem(name = "isDisplayLabelInList", false)
    }

    /**
     * 是否开启再次锁定
     */
    val isRelockEnable by lazy {
        BooleanItem(name = "isRelockEnable", false)
    }

    /**
     * 是否仅允许通过暗码进入隐私模式（关闭首页长按 SloganBar 进入隐私模式的入口）。
     *
     * 为避免用户一旦关闭长按入口便再也无法进入，该开关需在 [hasEnteredPrivateBySecret]
     * 为 true（至少通过暗码进入过隐私模式一次）后才可开启。
     */
    val isPrivateLockBySecretOnly by lazy {
        BooleanItem(name = "isPrivateLockBySecretOnly", false)
    }

    /**
     * 是否曾经通过暗码进入过隐私模式。
     *
     * 作为「仅暗码模式」开关的解锁条件：用户至少已知晓一次暗码，
     * 才不会因为关闭长按入口而把自己锁在隐私模式之外。
     */
    val hasEnteredPrivateBySecret by lazy {
        BooleanItem(name = "hasEnteredPrivateBySecret", false)
    }

    /**
     * 是否开启视频滑块点击模式
     */
    val isVideoSliderTapEnable by lazy {
        BooleanItem(name = "isVideoSliderTapEnable", false)
    }

    /**
     * 是否使用NextPlayer的解码器
     */
    val useNextPlayerDecoder by lazy {
        BooleanItem(name = "useNextPlayerDecoder", false)
    }

    /**
     * 是否开启热键
     */
    val isHotKeyEnable by lazy {
        BooleanItem(name = "isHotKeyEnable", false)
    }

    /**
     * 播放暂停按键的Code
     */
    val playPauseKeyCode by lazy {
        IntItem(name = "playPauseKeyCode", 0)
    }

    /**
     * 向上按键的Code
     */
    val upKeyCode by lazy {
        IntItem(name = "upKeyCode", 0)
    }

    /**
     * 向下按键的Code
     */
    val downKeyCode by lazy {
        IntItem(name = "downKeyCode", 0)
    }

    /**
     * 向右按键的Code
     */
    val rightKeyCode by lazy {
        IntItem(name = "rightKeyCode", 0)
    }

    /**
     * 向左按键的Code
     */
    val leftKeyCode by lazy {
        IntItem(name = "leftKeyCode", 0)
    }

    /**
     * 是否开启全屏模式
     */
    val isFullScreenEnable by lazy {
        BooleanItem(name = "isFullScreenEnable", false)
    }

    /**
     * 选择的视频公开目录
     */
    val selectPublicVideoDir by lazy {
        StringItem(name = "selectPublicVideoDir", "")
    }

    /**
     * 选择的照片公开目录
     */
    val selectPublicPhotoDir by lazy {
        StringItem(name = "selectPublicPhotoDir", "")
    }

    /**
     * 选择的视频私有目录
     */
    val selectPrivateVideoDir by lazy {
        StringItem(name = "selectPrivateVideoDir", "")
    }

    /**
     * 选择的照片私有目录
     */
    val selectPrivatePhotoDir by lazy {
        StringItem(name = "selectPrivatePhotoDir", "")
    }

    /**
     * 是否开启滚动标题
     */
    val isMarqueeTitle by lazy {
        BooleanItem(name = "isMarqueeTitle", true)
    }

    /**
     * 自定义的Slogan
     */
    val customSloganValue by lazy {
        StringItem(name = "customSloganValue", "")
    }

    /**
     * 允许首页 SloganBar 在列表离开顶部时自动隐藏（回到顶部后重新显示）。
     */
    val allowSloganBarAutoHide by lazy {
        BooleanItem(name = "allowSloganBarAutoHide", false)
    }

    /**
     * 是否开启循环播放
     */
    val isLoopPlayback by lazy {
        BooleanItem(name = "isLoopPlayback", true)
    }

    /**
     * 是否开启快速前进
     */
    val isQuickForwardEnable by lazy {
        BooleanItem(name = "isQuickForwardEnable", true)
    }

    /**
     * 快速前进时间
     */
    val quickForwardTime by lazy {
        FloatItem(name = "quickForwardTime", 5F)
    }

    /**
     * 是否开启「双击两侧快进 / 快退」（与按钮开关 [isQuickForwardEnable] 相互独立）
     */
    val isDoubleTapQuickSeekEnable by lazy {
        BooleanItem(name = "isDoubleTapQuickSeekEnable", true)
    }

    /**
     * 是否开启生物识别认证
     */
    val isBiometricAuth by lazy {
        BooleanItem(name = "isBiometricAuth", false)
    }

    /**
     * 是否开启Seek OSD
     */
    val isShowSeekOsd by lazy {
        BooleanItem(name = "isShowSeekOsd", true)
    }

    /**
     * 是否开启视频进度文本
     */
    val isShowVideoProgressText by lazy {
        BooleanItem(name = "isShowVideoProgressText", true)
    }

    /**
     * 是否开启触觉反馈（震动）。
     *
     * 仅作用于「无视觉反馈的模式进入」提示，详见 [com.lollipop.mediaflow.tools.AppHaptics]。
     */
    val isHapticFeedbackEnable by lazy {
        BooleanItem(name = "isHapticFeedbackEnable", true)
    }

    // ---- UI 特效（模糊）：参数均以「档位」形式存储 ----

    /**
     * 特效模式（[VisualEffectMode.key]）。
     *
     * 默认值按系统能力给出：Android 12+ 模糊 / Android 11- 无特效。
     */
    val visualEffectMode by lazy {
        IntItem(name = "visualEffectMode", VisualEffectCapability.defaultMode().key)
    }

    /** 画质档位（[PerformanceLevel.key]）。 */
    val effectPerformanceMode by lazy {
        IntItem(name = "effectPerformanceMode", PerformanceLevel.AUTO.key)
    }

    /** 模糊半径档位（[Level3.key]）。 */
    val blurRadiusLevel by lazy {
        IntItem(name = "blurRadiusLevel", Level3.MEDIUM.key)
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
