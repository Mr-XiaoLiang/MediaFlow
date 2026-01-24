package com.lollipop.mediaflow.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import com.lollipop.mediaflow.data.MediaChooser.MediaResult

class MediaChooser(
    private val store: MediaStore,
    private val result: ActivityResultCallback<MediaResult>
) : ActivityResultContract<Unit, MediaResult>() {

    companion object {
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

    fun register(activity: AppCompatActivity) {
        activity.registerForActivityResult(this, result)
    }

    fun remember(activity: AppCompatActivity, uri: Uri?, result: (Boolean) -> Unit) {
        uri ?: return
        val takeFlags: Int =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        activity.contentResolver.takePersistableUriPermission(uri, takeFlags)
        store.add(uri, result)
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

        fun remember(activity: AppCompatActivity, result: (Boolean) -> Unit) {
            launcher.remember(activity, uri, result)
        }
    }

}