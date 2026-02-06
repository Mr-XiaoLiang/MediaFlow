package com.lollipop.mediaflow.tools

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.edit
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.page.PrivateKeySettingActivity
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog

object PrivacyLock {

    private const val PREF_KEY = "PrivacyLock"
    private const val KEY_TARGET = "target"
    private const val KEY_PRIVATE_SETTING = "private_setting"

    const val PRIVATE_KEY_LENGTH = 4

    const val PRIVATE_KEY_MASK = 1000

    private var lockState = true

    val ICON_VIDEO = R.drawable.movie_24
    val ICON_PHOTO = R.drawable.photo_24

    /**
     * 是否已锁定
     * 当且仅当输入的数字序列与目标数字序列匹配时，才会解锁
     * 默认状态下，是锁定状态
     */
    val isLocked: Boolean
        get() {
            return target == 0 || lockState
        }

    /**
     * 是否是私有状态
     * 当且仅当未锁定时，才会显示私有内容
     */
    val privateVisibility: Boolean
        get() = !isLocked

    private var currentWindow = 0
    private var target = 0

    var privateSetting: Boolean = false
        private set

    private val log by lazy {
        registerLog()
    }

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE)
    }

    fun findByIconId(iconId: Int): IconKey? {
        for (iconKey in IconKey.entries) {
            if (iconKey.iconId == iconId) {
                return iconKey
            }
        }
        return null
    }

    fun findByKey(key: Int): IconKey? {
        for (iconKey in IconKey.entries) {
            if (iconKey.key == key) {
                return iconKey
            }
        }
        return null
    }

    fun loadKey(context: Context) {
        try {
            getPreferences(context).also {
                target = it.getInt(KEY_TARGET, 0)
                log.i("loadKey.target=$target")
                privateSetting = it.getBoolean(KEY_PRIVATE_SETTING, true)
                log.i("loadKey.privateSetting=$privateSetting")
            }
        } catch (e: Exception) {
            log.e("loadKey", e)
        }
    }

    /**
     * 关闭私有状态
     * 所以此时会需要将状态保存到 SharedPreferences 中
     */
    fun setClosePrivate(context: Context) {
        privateSetting = false
        target = 0
        getPreferences(context).edit {
            putBoolean(KEY_PRIVATE_SETTING, false)
            putInt(KEY_TARGET, 0)
        }
    }

    /**
     * 跳过设置密码
     * 所以此时是只标记内存，而不设置到 SharedPreferences 中
     */
    fun skipSetting() {
        privateSetting = false
        target = 0
    }

    /**
     * 保存密码
     * 所以此时会需要将状态保存到 SharedPreferences 中
     */
    fun saveKey(context: Context, target: Int) {
        try {
            getPreferences(context).also {
                it.edit {
                    putInt(KEY_TARGET, target)
                    putBoolean(KEY_PRIVATE_SETTING, false)
                }
            }
            this.privateSetting = false
            this.target = target
            log.i("setKey.target=$target")
        } catch (e: Exception) {
            log.e("setKey", e)
        }
    }

    fun feed(digit: IconKey) {
        if (target == 0) {
            // 不设置密码，那么说明，不需要密码验证
            return
        }
        // 锁定的情况下，才需要判断，否则就直接返回 false
        if (lockState) {
            // 保持 window 只有 4 位：先丢掉最高位，再塞入新数字
            currentWindow = (currentWindow % PRIVATE_KEY_MASK) * 10 + digit.key
            if (currentWindow == target) {
                // 密码正确，解锁
                lockState = false
            }
        }
    }

    fun openPrivateKeyManager(context: Context) {
        context.startActivity(Intent(context, PrivateKeySettingActivity::class.java).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        })
    }

    enum class IconKey(
        val iconId: Int,
        val key: Int
    ) {
        VIDEO(ICON_VIDEO, 1),
        PHOTO(ICON_PHOTO, 2);
    }

}