package com.lollipop.mediaflow.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import com.lollipop.mediaflow.data.MediaChooser.MediaResult
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog

class MediaChooser(
    private val result: ActivityResultCallback<MediaResult>
) : ActivityResultContract<Unit, MediaResult>() {

    companion object {

        fun hasWritePermission(context: Context, rootUri: String): Boolean {
            // 获取系统当前持有的所有持久化 URI 权限列表
            val persistedPermissions = context.contentResolver.persistedUriPermissions
            for (uriPermission in persistedPermissions) {
                if (uriPermission.uri.toString() == rootUri) {
                    return uriPermission.isWritePermission
                }
            }
            return false
        }

        fun findPermissionValid(context: Context, rootUri: Set<Uri>): List<Uri> {
            // 获取系统当前持有的所有持久化 URI 权限列表
            val persistedPermissions = context.contentResolver.persistedUriPermissions
            return persistedPermissions.filter {
                rootUri.contains(it.uri) && it.isReadPermission
            }.map {
                it.uri
            }
        }
    }

    private var launcher: ActivityResultLauncher<Unit>? = null

    fun register(activity: ComponentActivity) {
        launcher = activity.registerForActivityResult(this, result)
    }

    fun launch() {
        launcher?.launch(Unit)
    }

    fun remember(activity: Activity, uri: Uri?, store: MediaStore?, result: (Boolean) -> Unit) {
        uri ?: return
        val takeFlags: Int =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        activity.contentResolver.takePersistableUriPermission(uri, takeFlags)
        store?.add(uri, result)
    }

    override fun createIntent(
        context: Context,
        input: Unit
    ): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
    }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?
    ): MediaResult {
        if (resultCode != AppCompatActivity.RESULT_OK) {
            return MediaResult(null, this)
        }
        return MediaResult(intent?.data, this)
    }

    class MediaResult(
        val uri: Uri?,
        private val launcher: MediaChooser
    ) {
        val available: Boolean
            get() {
                return uri != null
            }

        fun remember(activity: Activity, store: MediaStore, result: (Boolean) -> Unit) {
            launcher.remember(activity = activity, uri = uri, store = store, result = result)
        }

        fun remember(activity: Activity) {
            launcher.remember(activity = activity, uri = uri, store = null, result = {})
        }
    }

}