package com.lollipop.mediaflow.tools

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import com.lollipop.mediaflow.data.MediaLayout
import com.lollipop.mediaflow.data.MediaType
import com.lollipop.mediaflow.data.MediaVisibility
import com.lollipop.mediaflow.page.play.PhotoFlowActivity
import com.lollipop.mediaflow.page.play.PhotoGalleryActivity
import com.lollipop.mediaflow.page.play.VideoFlowActivity

class MediaPlayLauncher(
    private val result: ActivityResultCallback<MediaIndex?>
) : ActivityResultContract<MediaPlayLauncher.LaunchParams, MediaIndex?>() {

    companion object {
        const val EXTRA_MEDIA_VISIBILITY = "extra_media_visibility"
        const val EXTRA_MEDIA_POSITION = "extra_media_position"
        const val EXTRA_MEDIA_TYPE = "extra_media_type"

        fun params(): Index {
            return Index()
        }

        private fun createIntent(
            context: Context,
            visibility: MediaVisibility,
            position: Int,
            type: MediaType,
            target: Class<out Activity>
        ): Intent {
            val intent = Intent(context, target)
            intent.putExtra(EXTRA_MEDIA_VISIBILITY, visibility.key)
            intent.putExtra(EXTRA_MEDIA_POSITION, position)
            intent.putExtra(EXTRA_MEDIA_TYPE, type.dataKey)
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return intent
        }

        private fun saveState(activity: Activity, state: Bundle?, position: Int) {
            state?.putInt(EXTRA_MEDIA_POSITION, position)
            val srcIntent = activity.intent
            activity.setResult(
                Activity.RESULT_OK,
                Intent().apply {
                    putExtra(
                        EXTRA_MEDIA_VISIBILITY,
                        getMediaVisibility(intent = srcIntent, savedState = null)
                    )
                    putExtra(
                        EXTRA_MEDIA_POSITION,
                        position
                    )
                    putExtra(
                        EXTRA_MEDIA_TYPE,
                        getMediaType(intent = srcIntent, savedState = null)
                    )
                }
            )
        }

        private fun resumeState(activity: Activity, state: Bundle?): MediaIndex {
            return MediaIndex(
                visibility = getMediaVisibility(intent = activity.intent, savedState = state),
                position = getMediaPosition(intent = activity.intent, savedState = state),
                type = getMediaType(intent = activity.intent, savedState = state)
            )
        }

        private fun getMediaVisibility(
            intent: Intent?,
            savedState: Bundle?,
        ): MediaVisibility {
            savedState?.getString(EXTRA_MEDIA_VISIBILITY)?.let {
                if (it.isNotEmpty()) {
                    return MediaVisibility.findByKey(it)
                }
            }
            intent?.getStringExtra(EXTRA_MEDIA_VISIBILITY)?.let {
                if (it.isNotEmpty()) {
                    return MediaVisibility.findByKey(it)
                }
            }
            return MediaVisibility.Public
        }

        private fun getMediaPosition(intent: Intent?, savedState: Bundle?, def: Int = 0): Int {
            savedState?.getInt(EXTRA_MEDIA_POSITION, -1)?.let {
                if (it >= 0) {
                    return it
                }
            }
            intent?.getIntExtra(EXTRA_MEDIA_POSITION, -1)?.let {
                if (it >= 0) {
                    return it
                }
            }
            return def
        }

        private fun getMediaType(intent: Intent?, savedState: Bundle?): MediaType {
            savedState?.getString(EXTRA_MEDIA_TYPE)?.let {
                if (it.isNotEmpty()) {
                    val type = MediaType.findByKey(it)
                    if (type != null) {
                        return type
                    }
                }
            }
            intent?.getStringExtra(EXTRA_MEDIA_TYPE)?.let {
                if (it.isNotEmpty()) {
                    val type = MediaType.findByKey(it)
                    if (type != null) {
                        return type
                    }
                }
            }
            return MediaType.Image
        }

    }

    private var launcher: ActivityResultLauncher<LaunchParams>? = null

    fun register(activity: ComponentActivity) {
        launcher = activity.registerForActivityResult(this, result)
    }

    fun launch(
        visibility: MediaVisibility = MediaVisibility.Public,
        position: Int = 0,
        type: MediaType = MediaType.Image,
        layout: MediaLayout = MediaLayout.Flow
    ) {
        launcher?.launch(
            LaunchParams(
                visibility = visibility,
                position = position,
                type = type,
                layout = layout
            )
        )
    }

    override fun createIntent(
        context: Context,
        input: LaunchParams
    ): Intent {
        val target = when (input.layout) {
            MediaLayout.Flow -> {
                when (input.type) {
                    MediaType.Image -> PhotoFlowActivity::class.java
                    MediaType.Video -> VideoFlowActivity::class.java
                }
            }

            MediaLayout.Gallery -> {
                when (input.type) {
                    MediaType.Image -> PhotoGalleryActivity::class.java
                    MediaType.Video -> VideoFlowActivity::class.java
                }
            }
        }
        return createIntent(
            context,
            visibility = input.visibility,
            position = input.position,
            type = input.type,
            target = target
        )
    }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?
    ): MediaIndex? {
        intent ?: return null
        return MediaIndex(
            visibility = getMediaVisibility(intent = intent, savedState = null),
            position = getMediaPosition(intent = intent, savedState = null),
            type = getMediaType(intent = intent, savedState = null)
        )
    }

    class LaunchParams(
        val visibility: MediaVisibility,
        val position: Int,
        val type: MediaType,
        val layout: MediaLayout
    )

    class Index {

        var currentPosition = 0
            private set
        var params: MediaIndex = MediaIndex.DEFAULT
            private set

        val visibility: MediaVisibility
            get() {
                return params.visibility
            }

        val type: MediaType
            get() {
                return params.type
            }

        val paramsPosition: Int
            get() {
                return params.position
            }

        fun onCreate(activity: Activity, savedInstanceState: Bundle?) {
            val index = resumeState(activity, savedInstanceState)
            currentPosition = index.position
            params = index
        }

        fun onSelected(activity: Activity, position: Int) {
            currentPosition = position
            saveState(activity, null, position)
        }

        fun onSaveInstanceState(
            activity: Activity,
            outState: Bundle,
            outPersistentState: PersistableBundle
        ) {
            saveState(activity, outState, currentPosition)
        }

    }

}

class MediaIndex(
    val visibility: MediaVisibility = MediaVisibility.Public,
    val position: Int = 0,
    val type: MediaType = MediaType.Image
) {

    companion object {
        val DEFAULT = MediaIndex()
    }

}
