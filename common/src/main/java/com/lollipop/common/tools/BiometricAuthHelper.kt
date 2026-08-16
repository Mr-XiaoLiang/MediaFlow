package com.lollipop.common.tools

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricAuthHelper {

    /**
     * 认证结果回调
     */
    fun interface AuthCallback {
        fun onResult(result: AuthResult)
    }

    /**
     * 检查当前设备是否满足认证条件
     *
     * @param context 上下文
     * @param allowDeviceCredential 是否允许使用系统锁屏 PIN/密码/图案
     */
    fun canAuthenticate(context: Context, allowDeviceCredential: Boolean = true): Boolean {
        val biometricManager = BiometricManager.from(context)

        return if (allowDeviceCredential) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11 (API 30)+ : 直接支持组合检查
                biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
            } else {
                // Android 10 (API 29) 及以下：分步检查生物识别或系统凭据
                val canBiometric =
                    biometricManager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
                val keyguardManager =
                    context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                val canDeviceCredential = keyguardManager?.isDeviceSecure == true

                canBiometric || canDeviceCredential
            }
        } else {
            // 仅使用强生物识别（指纹/人脸）
            biometricManager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        }
    }

    /**
     * 发起生物识别 / 系统密码验证
     *
     * @param activity 必须是 FragmentActivity
     * @param title 弹窗标题
     * @param subtitle 弹窗副标题
     * @param allowDeviceCredential 是否允许回退到系统密码/PIN（设为 true 时无需配置 negativeButtonText）
     * @param negativeButtonText 当 allowDeviceCredential 为 false 时，取消按钮的文案
     * @param callback 结果回调
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String = "",
        allowDeviceCredential: Boolean = true,
        negativeButtonText: String = "",
        callback: AuthCallback
    ) {
        // 1. 检查可用性，不可用直接回调错误
        if (!canAuthenticate(activity, allowDeviceCredential)) {
            callback.onResult(
                AuthResult.Error(
                    BiometricPrompt.ERROR_HW_UNAVAILABLE,
                    "设备不支持生物识别或未设置系统锁屏密码"
                )
            )
            return
        }

        // 2. 构建 PromptInfo（处理 API 29 与 30+ 互斥规则）
        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)

        if (subtitle.isNotEmpty()) {
            promptInfoBuilder.setSubtitle(subtitle)
        }

        if (allowDeviceCredential) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ 使用位掩码
                promptInfoBuilder.setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            } else {
                // Android 10 及以下使用旧 API，且**绝对不能**设置 NegativeButton
                @Suppress("DEPRECATION")
                promptInfoBuilder.setDeviceCredentialAllowed(true)
            }
        } else {
            // 仅指纹模式：必须设置 NegativeButton，且只能使用 BIOMETRIC_STRONG
            promptInfoBuilder.setAllowedAuthenticators(BIOMETRIC_STRONG)
            val text = negativeButtonText.ifEmpty {
                activity.getString(com.lollipop.common.R.string.biometric_auth_negative_button)
            }
            promptInfoBuilder.setNegativeButtonText(text)
        }

        val promptInfo = promptInfoBuilder.build()

        // 3. 构建 BiometricPrompt 并发起验证
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    callback.onResult(AuthResult.Success(result))
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    callback.onResult(AuthResult.Error(errorCode, errString))
                }
            }
        )

        biometricPrompt.authenticate(promptInfo)
    }

    sealed class AuthResult {

        class Success(val result: BiometricPrompt.AuthenticationResult) : AuthResult()

        class Error(val errorCode: Int, val errString: CharSequence) : AuthResult()

    }

}