package com.lollipop.mediaflow.tools

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

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
    fun isPrivateLockBiometric(context: Context): Boolean {
        return Preferences.isBiometricAuth.get() && BiometricAuthHelper.canAuthenticate(context)
    }

    /**
     * 进入隐私模式（解锁）。
     *
     * 作为中性的状态操作，**不关心调用来源**：是否需要生物识别、是否来自暗码入口，
     * 都由调用方自行负责。本控制器只维护「锁定 / 解锁」这一个状态。
     */
    fun enterPrivateMode() {
        lockStateImpl.value = false
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