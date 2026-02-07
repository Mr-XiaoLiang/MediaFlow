package com.lollipop.mediaflow.tools

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.lollipop.mediaflow.data.MediaVisibility

object MediaPageHelper {

    const val EXTRA_MEDIA_VISIBILITY = "extra_media_visibility"
    const val EXTRA_POSITION = "extra_position"

    fun start(
        context: Context,
        mediaVisibility: MediaVisibility,
        position: Int,
        target: Class<out Activity>
    ) {
        val intent = Intent(context, target)
        intent.putExtra(EXTRA_MEDIA_VISIBILITY, mediaVisibility.key)
        intent.putExtra(EXTRA_POSITION, position)
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun getMediaVisibility(intent: Intent): MediaVisibility {
        return intent.getStringExtra(EXTRA_MEDIA_VISIBILITY)?.let {
            MediaVisibility.findByKey(it)
        } ?: MediaVisibility.Public
    }

    fun getMediaPosition(intent: Intent): Int {
        return intent.getIntExtra(EXTRA_POSITION, 0)
    }

    fun getMediaVisibility(activity: Activity): MediaVisibility {
        return getMediaVisibility(activity.intent)
    }

    fun getMediaPosition(activity: Activity): Int {
        return getMediaPosition(activity.intent)
    }

}