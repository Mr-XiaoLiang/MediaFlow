package com.lollipop.mediaflow.page.privacy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lollipop.mediaflow.tools.PrivacyLock

/**
 * 隐私模式暗码广播接收器。
 *
 * 监听系统暗码 `*#*#66339#*#*`（[ACTION_SECRET_CODE]，host 由 AndroidManifest 声明），
 * 按当前配置决定进入隐私模式的方式：
 *
 * 1. 已处于隐私模式：仅把首页带回前台，重复输入暗码不改变状态；
 * 2. 未启用生物识别：直接进入隐私模式并打开首页；
 * 3. 已启用生物识别：交由 [PrivacyBiometricGateActivity] 承载验证，验证通过后再进入隐私模式。
 */
class SecretCodeReceiver : BroadcastReceiver() {

    companion object {

        /**
         * 系统暗码广播 action。
         *
         * 直接使用字面量而非 `Telephony.SECRET_CODE_ACTION`：后者未包含在当前 compileSdk 的
         * public API 中，无法被 Kotlin 解析。此值需与 AndroidManifest 的 intent-filter 保持一致。
         */
        private const val ACTION_SECRET_CODE = "android.provider.Telephony.SECRET_CODE"

    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SECRET_CODE) {
            return
        }
        if (PrivacyLock.privateVisibility) {
            // 已是隐私模式：暗码只负责把首页带回前台
            PrivacyEntry.openHome(context)
            return
        }
        if (PrivacyLock.isPrivateLockBiometric(context)) {
            // 启用了生物识别：先经过承载页验证，通过后再进入隐私模式
            PrivacyBiometricGateActivity.start(context)
            return
        }
        // 未启用生物识别：直接进入隐私模式并打开首页
        PrivacyEntry.enterBySecretCode(context)
    }

}
