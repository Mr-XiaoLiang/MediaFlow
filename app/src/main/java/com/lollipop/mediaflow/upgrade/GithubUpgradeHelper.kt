package com.lollipop.mediaflow.upgrade

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object GithubUpgradeHelper {

    private fun getInstallPendingIntent(context: Context, apkFile: File): PendingIntent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }


}