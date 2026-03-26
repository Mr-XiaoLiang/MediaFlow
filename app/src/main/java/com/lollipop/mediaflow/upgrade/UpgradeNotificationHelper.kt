package com.lollipop.mediaflow.upgrade

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.tools.QuickResult
import com.lollipop.mediaflow.tools.safeRun


class UpgradeNotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "UpgradeNotification"
        val CHANNEL_NAME = R.string.notification_channel_label_upgrade

        val NOTIFICATION_TITLE = R.string.notification_title_upgrade
        val NOTIFICATION_ACTION_INSTALL = R.string.notification_action_upgrade_install

        const val NOTIFICATION_ID = 20260327
    }

    private val notificationManager: NotificationManager? by lazy {
        context.getSystemService(NotificationManager::class.java)
    }

    private val requestPermissionLauncher by lazy {
        ActivityResultContracts.RequestPermission()
    }

    private fun createChannel() {
        notificationManager?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(CHANNEL_NAME),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    private fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    /**
     * 在请求了权限之后的操作，如果权限没有通过，那么不会有任何反馈
     */
    fun checkPermission(): QuickResult<Boolean> {
        return safeRun {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = hasPermission()
                if (!hasPermission) {
                    context.startActivity(
                        requestPermissionLauncher.createIntent(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    )
                    return@safeRun false
                }
            }
            true
        }
    }

    fun progress(
        progress: Int,
        title: Int = NOTIFICATION_TITLE,
        pendingIntent: PendingIntent? = null,
        ongoing: Boolean = true
    ): QuickResult<Boolean> {
        return notification(
            title = title,
            progress = progress,
            hasProgress = true,
            pendingIntent = pendingIntent,
            ongoing = ongoing
        )
    }

    fun complete(
        pendingIntent: PendingIntent,
        title: Int = NOTIFICATION_TITLE,
        ongoing: Boolean = false
    ): QuickResult<Boolean> {
        return notification(
            title = title,
            progress = 0,
            hasProgress = false,
            pendingIntent = pendingIntent,
            ongoing = ongoing
        )
    }

    private fun notification(
        title: Int,
        progress: Int,
        hasProgress: Boolean,
        pendingIntent: PendingIntent?,
        ongoing: Boolean
    ): QuickResult<Boolean> {
        return safeRun {
            val themeColor = ContextCompat.getColor(context, R.color.notification_color)
            val builder = Notification.Builder(context, CHANNEL_ID)
                // 1. 必须是 Ongoing (持续性通知)
                .setOngoing(ongoing)
                // 2. 必须设置标题 (没有标题会被排除)
                .setContentTitle(context.getString(title))
                // 3. 必须请求着色 (这是非通话类通知被提升的关键)
                .setColorized(true)
                // 设置一个品牌色
                .setColor(themeColor)

            if (hasProgress) {
                val max = 100
                val current = progress.coerceIn(0, max)
                // 4. 展示进度条 (系统会自动识别为可提升样式)
//                builder.setProgress(max, current, progress < 0)
                builder.setStyle(
                    Notification.ProgressStyle()
                        .setProgress(current)
                        .addProgressSegment(
                            Notification.ProgressStyle.Segment(current).setColor(themeColor)
                        )
                        .addProgressSegment(
                            Notification.ProgressStyle.Segment(max - current).setColor(Color.GRAY)
                        )
                )
                builder.setShortCriticalText("${current}%")
            } else {
                builder.setShortCriticalText(context.getString(NOTIFICATION_ACTION_INSTALL))
            }
            if (pendingIntent != null) {
                // 5. 设置点击后的 PendingIntent (方便用户快速打开)
                builder.setContentIntent(pendingIntent)
            }
            // 6. 使用系统标准样式 (千万不要使用 RemoteViews 自定义布局)
            builder.setSmallIcon(R.drawable.downloading_24)
                // 7. 设置分类为进度类
                .setCategory(Notification.CATEGORY_PROGRESS)
            notificationManager?.notify(NOTIFICATION_ID, builder.build())
            true
        }
    }

}