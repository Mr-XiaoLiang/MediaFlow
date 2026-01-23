package com.lollipop.mediaflow.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity

object MediaChooser {

    class Launcher(
        private val store: MediaStore,
        private val result: ActivityResultCallback<Uri?>
    ) : ActivityResultContract<Unit, Uri?>() {

        fun register(activity: AppCompatActivity) {
            activity.registerForActivityResult(this, result)
        }

        fun remember(activity: AppCompatActivity, uri: Uri?) {
            uri ?: return
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            activity.contentResolver.takePersistableUriPermission(uri, takeFlags)
            store.add(uri)
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
        ): Uri? {
            return intent?.data
        }
    }

}