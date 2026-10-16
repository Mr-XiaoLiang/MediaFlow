package com.lollipop.mediaflow.page.privacy

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.tools.BiometricAuthHelper
import com.lollipop.mediaflow.tools.PrivacyLock
import com.lollipop.mediaflow.ui.BasicComposeActivity
import com.lollipop.mediaflow.ui.theme.currentThemeColor

/** Logo 宽高比（取自 drawable/ic_mediaflow 的 viewport：2663 x 750）。 */
private const val LogoAspectRatio = 2663F / 750F

/** 中央 Logo 宽度占屏宽比例。 */
private const val LogoWidthRatio = 0.45F

/** 底部关闭图标的触控区与图形尺寸。 */
private val CloseTouchSize = 48.dp
private val CloseIconSize = 32.dp

/** 关闭图标距底部安全区的间距。 */
private val CloseBottomMargin = 32.dp

/**
 * 生物识别中间页：进入隐私模式前的「空页面」载体。
 *
 * 页面只保留两处内容——屏幕中央的 Logo 与屏幕下方居中的无背景关闭图标，
 * 用于在没有视觉干扰的前提下承载生物识别弹窗。
 *
 * ## 生命周期职责
 *
 * 页面**不在 `onCreate` 中发起验证**，而是把决策放在 [onResume]：
 * 生物识别弹窗的显示 / 消失会反复触发 `onPause` → `onResume`，
 * 因此需要有明确的状态机避免重复唤起（见 [AuthState]）。
 *
 * 每个 `onResume` 只做一件事：
 * - 已处于隐私模式 → 无需停留，回到首页；
 * - 未启用生物识别 → 直接进入隐私模式并回到首页；
 * - 尚未发起过验证 → 发起验证；
 * - 验证进行中 / 已失败 → 保持现状，等待用户操作。
 *
 * 用户在验证失败后主动点击关闭图标或返回键，视为「放弃操作」，回到普通首页。
 */
class PrivacyBiometricGateActivity : BasicComposeActivity() {

    companion object {

        /** 启动本页（供暗码广播等非 Activity 上下文调用）。 */
        fun start(context: Context) {
            context.startActivity(
                Intent(context, PrivacyBiometricGateActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }

    }

    /** 首页自绘外壳（纯色空页），无需 Scaffold 的内边距分发。 */
    override val useScaffold: Boolean = false

    /** 验证流程状态机：避免生物识别弹窗的 pause/resume 造成重复唤起。 */
    private enum class AuthState {
        /** 尚未发起验证。 */
        Idle,

        /** 验证进行中（弹窗显示期间）。 */
        Running,

        /** 验证已结束且未通过，停留在本页等待用户关闭。 */
        Finished,
    }

    private var authState = AuthState.Idle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 返回键与关闭图标同样视为「放弃操作」，回到普通首页，避免直接退回桌面
        onBackPressedDispatcher.addCallback(this) {
            abandon()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isFinishing) {
            return
        }
        when {
            // 已处于隐私模式（例如并发的暗码触发）：无需停留
            PrivacyLock.privateVisibility -> {
                PrivacyEntry.openHome(this)
                finish()
            }

            // 未启用生物识别：直接进入隐私模式
            !PrivacyLock.isPrivateLockBiometric(this) -> {
                PrivacyEntry.enterBySecretCode(this)
                finish()
            }

            // 已发起过验证（进行中或已失败）：不再自动重复唤起
            authState != AuthState.Idle -> Unit

            else -> requestBiometricAuth()
        }
    }

    private fun requestBiometricAuth() {
        authState = AuthState.Running
        BiometricAuthHelper.authenticate(
            activity = this,
            title = getString(R.string.title_biometric_auth),
            subtitle = getString(R.string.app_name)
        ) { result ->
            if (result is BiometricAuthHelper.AuthResult.Success) {
                PrivacyEntry.enterBySecretCode(this)
                finish()
            } else {
                // 验证失败 / 取消：停留在本页，等待用户主动关闭（放弃操作）
                authState = AuthState.Finished
            }
        }
    }

    /** 放弃进入隐私模式：关闭本页并回到普通首页。 */
    private fun abandon() {
        PrivacyEntry.openHome(this)
        finish()
    }

    @Composable
    override fun Content(innerPadding: PaddingValues) {
        val themeColor = currentThemeColor()
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(themeColor.windowBackground),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_mediaflow),
                contentDescription = null,
                colorFilter = ColorFilter.tint(themeColor.buttonText),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(LogoWidthRatio)
                    .aspectRatio(LogoAspectRatio)
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = insets.calculateBottomPadding() + CloseBottomMargin)
                    .size(CloseTouchSize)
                    .clickable(onClick = ::abandon),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.content_desc_close_private_gate),
                    tint = themeColor.buttonText,
                    modifier = Modifier.size(CloseIconSize)
                )
            }
        }
    }

}
