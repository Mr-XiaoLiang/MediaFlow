package com.lollipop.mediaflow.tools

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import com.lollipop.common.tools.BiometricAuthHelper
import com.lollipop.common.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.page.settings.PrivateKeySettingActivity

object PrivacyLock {

    private const val PREF_KEY = "PrivacyLock"
    private const val KEY_TARGET = "target"
    private const val KEY_PRIVATE_SETTING = "private_setting"

    const val PRIVATE_KEY_LENGTH = 4

    const val PRIVATE_KEY_MASK = 1000

    private val lockStateImpl = mutableStateOf(true)

    private var lastTouchTime = 0L

    private var target = 0

    val ICON_VIDEO = R.drawable.movie_24
    val ICON_PHOTO = R.drawable.photo_24

    /**
     * 是否已锁定
     * 当且仅当输入的数字序列与目标数字序列匹配时，才会解锁
     * 默认状态下，是锁定状态
     */
    val isLocked: Boolean
        get() {
            return lockState.value
        }

    val lockState: State<Boolean>
        get() {
            return lockStateImpl
        }

    /**
     * 是否是私有状态
     * 当且仅当未锁定时，才会显示私有内容
     */
    val privateVisibility: Boolean
        get() = !isLocked

    private var currentWindow = 0

    var privateSetting: Boolean = false
        private set

    private val log by lazy {
        registerLog()
    }

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE)
    }

    fun controller(filter: LockStateFilter? = null): Controller {
        return Controller(filter = filter)
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

    private fun changedState(locked: Boolean, filter: LockStateFilter?) {
        if (filter == null) {
            lockStateImpl.value = locked
            return
        }
        filter.onStateChanged(locked) {
            lockStateImpl.value = it
        }
    }

    private fun buildKeyWindow(digit: IconKey): Int {
        return (currentWindow % PRIVATE_KEY_MASK) * 10 + digit.key
    }

    fun openPrivateKeyManager(context: Context) {
        context.startActivity(Intent(context, PrivateKeySettingActivity::class.java).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        })
    }

    /**
     * 是否启用了生物识别
     */
    fun isPrivateLockBiometric(activity: Activity): Boolean {
        return Preferences.isBiometricAuth.get() && BiometricAuthHelper.canAuthenticate(activity)
    }

    class Controller(private val filter: LockStateFilter?) {

        fun feed(digit: IconKey) {
            if (target == 0) {
                // 不设置密码，那么说明，不需要密码验证
                if (!isLocked) {
                    // 没有锁定，就锁上
                    changedState(locked = true, filter = filter)
                }
                return
            }
            // 引入超时重试
            val now = System.currentTimeMillis()
            if (now - lastTouchTime > 500) {
                // 超过间隔，就重置窗口
                currentWindow = 0
            }
            // 记录最后一次点击时间
            lastTouchTime = now
            // 锁定的情况下，才需要判断，否则就直接返回 false
            if (lockState.value) {
                // 保持 window 只有 4 位：先丢掉最高位，再塞入新数字
                currentWindow = buildKeyWindow(digit)
                if (currentWindow == target) {
                    // 密码正确，解锁
                    changedState(locked = false, filter = filter)
                    currentWindow = 0
                }
            } else {
                // 保持 window 只有 4 位：先丢掉最高位，再塞入新数字
                currentWindow = buildKeyWindow(digit)
                if (currentWindow == target && Preferences.isRelockEnable.get()) {
                    // 密码正确，锁定
                    changedState(locked = true, filter = filter)
                    currentWindow = 0
                }
            }
        }

    }

    enum class IconKey(
        val iconId: Int,
        val key: Int
    ) {
        VIDEO(ICON_VIDEO, 1),
        PHOTO(ICON_PHOTO, 2);
    }

    fun interface LockStateFilter {

        fun onStateChanged(isLocked: Boolean, callback: (newState: Boolean) -> Unit)

    }

}