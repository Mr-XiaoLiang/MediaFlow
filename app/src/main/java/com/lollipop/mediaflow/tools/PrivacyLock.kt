package com.lollipop.mediaflow.tools

import android.content.Context
import androidx.core.content.edit
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog

object PrivacyLock {

    private const val PREF_KEY = "PrivacyLock"
    private const val KEY_TARGET = "target"

    /**
     * 是否已锁定
     * 当且仅当输入的数字序列与目标数字序列匹配时，才会解锁
     * 默认状态下，是锁定状态
     */
    var isLocked = true
        private set

    private var currentWindow = 0
    private var target = 1234

    private val log by lazy {
        registerLog()
    }

    fun loadKey(context: Context) {
        try {
            context.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE).also {
                target = it.getInt(KEY_TARGET, 0)
                log.i("loadKey.target=$target")
            }
        } catch (e: Exception) {
            log.e("loadKey", e)
        }
    }

    fun saveKey(context: Context, target: Int) {
        try {
            context.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE).also {
                it.edit {
                    putInt(KEY_TARGET, target)
                }
                this.target = target
                log.i("setKey.target=$target")
            }
        } catch (e: Exception) {
            log.e("setKey", e)
        }
    }

    fun feed(digit: Int): Boolean {
        // 保持 window 只有 4 位：先丢掉最高位，再塞入新数字
        currentWindow = (currentWindow % 1000) * 10 + digit
        return currentWindow == target
    }

}