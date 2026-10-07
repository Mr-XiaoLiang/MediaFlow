package com.lollipop.mediaflow.tools

import android.app.Activity
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.lollipop.common.tools.BiometricAuthHelper

object PrivacyLock {

    private val lockStateImpl = mutableStateOf(true)

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

    fun controller(filter: LockStateFilter? = null): Controller {
        return Controller(filter = filter)
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

    /**
     * 是否启用了生物识别
     */
    fun isPrivateLockBiometric(activity: Activity): Boolean {
        return Preferences.isBiometricAuth.get() && BiometricAuthHelper.canAuthenticate(activity)
    }

    class Controller(private val filter: LockStateFilter?) {

        fun requestToggle() {
            if (Preferences.isRelockEnable.get()) {
                changedState(locked = !isLocked, filter = filter)
            } else if (isLocked) {
                changedState(locked = false, filter = filter)
            }
        }

    }

    fun interface LockStateFilter {

        fun onStateChanged(isLocked: Boolean, callback: (newState: Boolean) -> Unit)

    }

}