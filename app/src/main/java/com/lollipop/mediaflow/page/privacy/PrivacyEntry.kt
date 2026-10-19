package com.lollipop.mediaflow.page.privacy

import android.content.Context
import android.content.Intent
import com.lollipop.mediaflow.MainActivity
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.mediaflow.tools.PrivacyLock

/**
 * 隐私模式相关的页面跳转入口。
 *
 * 集中「打开 / 回到首页」的 Intent 组装，供暗码广播与生物识别中间页复用，
 * 避免多处各写一份 flags 导致行为分叉。
 */
object PrivacyEntry {

    /**
     * 通过暗码进入隐私模式：解锁 + 记录「曾经通过暗码进入过」的标识 + 回到首页。
     *
     * 此组合动作归属「暗码入口」一侧而非 [PrivacyLock]：
     * 模式控制器只维护状态，「通过什么方式进入、是否留痕」是入口的业务语义，二者职责分离。
     */
    fun enterBySecretCode(context: Context) {
        PrivacyLock.enterPrivateMode()
        Preferences.hasEnteredPrivateBySecret.set(true)
        openHome(context)
    }

    /**
     * 打开（或回到）首页。
     *
     * 叠加 [Intent.FLAG_ACTIVITY_CLEAR_TOP] 与 [Intent.FLAG_ACTIVITY_SINGLE_TOP]：
     * 首页已在任务栈中时直接回到它（不重建），否则新建；
     * 再叠加 [Intent.FLAG_ACTIVITY_NEW_TASK]，以兼容从 BroadcastReceiver 上下文启动。
     */
    fun openHome(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        context.startActivity(intent)
    }

}
