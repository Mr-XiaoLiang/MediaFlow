package com.lollipop.mediaflow.tools

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import com.lollipop.mediaflow.data.MediaLayout

object Preferences {

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
     * 归档文件夹地址
     */
    val archiveDirUri by lazy {
        StringItem(name = "archiveDirUri", "")
    }

    /**
     * 归档文件夹名字
     */
    val archiveDirName by lazy {
        StringItem(name = "archiveDirName", "")
    }

    /**
     * 是否开启快速播放模式
     */
    val isQuickPlayEnable by lazy {
        BooleanItem(name = "isQuickPlayEnable", def = false)
    }

    /**
     * 是否开启快速移动到回收站的模式
     */
    val isQuickArchiveEnable by lazy {
        BooleanItem(name = "isQuickArchiveEnable", def = false)
    }

    /**
     * 快速播放的自动模式
     */
    val quickPlayMode by lazy {
        MediaLayoutItem(name = "quickPlayMode", def = MediaLayout.Flow)
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

    abstract class TypedItem<T> {

        protected val stateImpl by lazy {
            mutableStateOf(getPreferencesValue())
        }

        val state: State<T>
            get() {
                return stateImpl
            }

        fun get(): T {
            return state.value
        }

        fun set(value: T) {
            this.stateImpl.value = value
            setPreferencesValue(value)
        }

        protected abstract fun getPreferencesValue(): T

        protected abstract fun setPreferencesValue(value: T)

    }

    class StringItem(
        val name: String,
        val def: String
    ) : TypedItem<String>() {
        override fun getPreferencesValue(): String {
            return PreferencesDelegate.get(name = name, def = def)
        }

        override fun setPreferencesValue(value: String) {
            PreferencesDelegate.set(name = name, value = value)
        }

    }

    class BooleanItem(
        val name: String,
        val def: Boolean
    ) : TypedItem<Boolean>() {
        override fun getPreferencesValue(): Boolean {
            return PreferencesDelegate.get(name = name, def = def)
        }

        override fun setPreferencesValue(value: Boolean) {
            PreferencesDelegate.set(name = name, value = value)
        }

    }

    class IntItem(
        val name: String,
        val def: Int
    ) : TypedItem<Int>() {
        override fun getPreferencesValue(): Int {
            return PreferencesDelegate.get(name = name, def = def)
        }

        override fun setPreferencesValue(value: Int) {
            PreferencesDelegate.set(name = name, value = value)
        }

    }

    class LongItem(
        val name: String,
        val def: Long
    ) : TypedItem<Long>() {
        override fun getPreferencesValue(): Long {
            return PreferencesDelegate.get(name = name, def = def)
        }

        override fun setPreferencesValue(value: Long) {
            PreferencesDelegate.set(name = name, value = value)
        }

    }

    class FloatItem(
        val name: String,
        val def: Float
    ) : TypedItem<Float>() {
        override fun getPreferencesValue(): Float {
            return PreferencesDelegate.get(name = name, def = def)
        }

        override fun setPreferencesValue(value: Float) {
            PreferencesDelegate.set(name = name, value = value)
        }

    }

    class MediaLayoutItem(
        val name: String,
        val def: MediaLayout
    ) : TypedItem<MediaLayout>() {
        override fun getPreferencesValue(): MediaLayout {
            return PreferencesDelegate.get(name = name, def = def.dataKey).let {
                MediaLayout.findByKey(it)
            } ?: MediaLayout.Flow
        }

        override fun setPreferencesValue(value: MediaLayout) {
            PreferencesDelegate.set(name, value.dataKey)
        }

    }

    private object PreferencesDelegate {
        private const val PREFERENCES_NAME = "MediaFlow"

        private var preferences: SharedPreferences? = null

        fun init(context: Context) {
            preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        }

        fun get(name: String, def: Int = 0): Int {
            return preferences?.getInt(name, def) ?: return def
        }

        fun set(name: String, value: Int) {
            preferences?.edit {
                putInt(name, value)
            }
        }

        fun get(name: String, def: Boolean = false): Boolean {
            return preferences?.getBoolean(name, def) ?: return def
        }

        fun set(name: String, value: Boolean) {
            preferences?.edit {
                putBoolean(name, value)
            }
        }

        fun get(name: String, def: Long = 0): Long {
            return preferences?.getLong(name, def) ?: return def
        }

        fun set(name: String, value: Long) {
            preferences?.edit {
                putLong(name, value)
            }
        }

        fun get(name: String, def: String = ""): String {
            return preferences?.getString(name, def) ?: return def
        }

        fun set(name: String, value: String) {
            preferences?.edit {
                putString(name, value)
            }
        }

        fun get(name: String, def: Float = 0F): Float {
            return preferences?.getFloat(name, def) ?: return def
        }

        fun set(name: String, value: Float) {
            preferences?.edit {
                putFloat(name, value)
            }
        }
    }

}