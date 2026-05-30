package com.lollipop.mediaflow.tools

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lollipop.common.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.data.MediaMetadata
import java.lang.ref.WeakReference

object PIPHelper {

    private val maxRational by lazy {
        Rational(239, 100)
    }

    private val minRational by lazy {
        Rational(100, 239)
    }

    private const val MIN_RATIO = 1f / 2.39f
    private const val MAX_RATIO = 2.39f / 1f

    fun registerPipActions(activity: ComponentActivity, onPipBroadcast: (Action) -> Unit) {
        PipBroadcastAdapter(activity, onPipBroadcast)
    }

    fun setParams(activity: Activity, metadata: MediaMetadata?, option: Option?) {
        val width = metadata?.width ?: 100
        val height = metadata?.height ?: 100
        if (metadata?.needRotate == true) {
            setParams(activity = activity, width = height, height = width, option = option)
        } else {
            setParams(activity = activity, width = width, height = height, option = option)
        }
    }

    fun setParams(
        activity: Activity,
        width: Int,
        height: Int,
        option: Option?
    ) {
        if (!Preferences.isPictureInPictureEnable.get()) {
            return
        }
        // 1. 防止极端比例崩溃，Android 限制比例必须在 1:2.39 到 2.39:1 之间
        val rawRatio = width.toFloat() / height.toFloat()
        val finalRatio = when {
            rawRatio > MAX_RATIO -> maxRational
            rawRatio < MIN_RATIO -> minRational
            else -> Rational(width, height)
        }

        val actionList = mutableListOf<RemoteAction>()
        if (option != null) {
            if (option.hasPrev && Preferences.isPipPrevEnable.get()) {
                actionList.add(Action.PREVIOUS.createRemoteAction(activity))
            }
            if (Preferences.isPipPlayEnable.get()) {
                // 这里只能二选一，play 和 pause 不能同时存在
                if (option.hasPlay) {
                    actionList.add(Action.PLAY.createRemoteAction(activity))
                } else if (option.hasPause) {
                    actionList.add(Action.PAUSE.createRemoteAction(activity))
                }
            }
            if (option.hasNext && Preferences.isPipNextEnable.get()) {
                actionList.add(Action.NEXT.createRemoteAction(activity))
            }
        }
        // 2. 构建新的参数
        val updatedParams = PictureInPictureParams.Builder()
            .setAspectRatio(finalRatio)
            .setAutoEnterEnabled(true)
            .setActions(actionList)
            .build()

        // 3. 动态注入（无论是在前台还是已经处于画中画，都会实时生效）
        activity.setPictureInPictureParams(updatedParams)
    }

    fun isInPictureInPictureMode(activity: Activity): Boolean {
        return activity.isInPictureInPictureMode
    }

    enum class Action(
        val key: String,
        val icon: Int,
        val title: Int,
    ) {
        PREVIOUS(
            key = "com.lollipop.mediaflow.PREVIOUS",
            icon = R.drawable.skip_previous_24,
            title = R.string.pip_button_skip_previous
        ),
        NEXT(
            key = "com.lollipop.mediaflow.NEXT",
            icon = R.drawable.skip_next_24,
            title = R.string.pip_button_skip_next
        ),
        PLAY(
            key = "com.lollipop.mediaflow.PLAY",
            icon = R.drawable.play_arrow_24,
            title = R.string.pip_button_play
        ),
        PAUSE(
            key = "com.lollipop.mediaflow.PAUSE",
            icon = R.drawable.pause_24,
            title = R.string.pip_button_pause
        );

        fun createRemoteAction(context: Context): RemoteAction {
            val titleValue = context.getString(title)
            return RemoteAction(
                Icon.createWithResource(context, icon),
                titleValue,
                titleValue,
                PendingIntent.getBroadcast(context, 0, Intent(key), PendingIntent.FLAG_IMMUTABLE)
            )
        }

    }

    class Option(
        val hasPrev: Boolean,
        val hasNext: Boolean,
        val hasPlay: Boolean,
        val hasPause: Boolean,
    )

    private class BroadcastWrapper(
        val onPipBroadcast: (Action) -> Unit
    ) : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            Action.entries.find { it.key == action }?.let {
                onPipBroadcast(it)
            }
        }
    }

    class PipBroadcastAdapter(
        activity: ComponentActivity,
        private val onPipBroadcast: (Action) -> Unit
    ) {

        private val log by lazy {
            registerLog()
        }

        private val activityRef = WeakReference(activity)

        private val broadcastWrapper = BroadcastWrapper(::onActionCallback)

        private val lifecycleObserver = LifecycleEventObserver { source, event ->

            when (event) {
                Lifecycle.Event.ON_START -> {
                    register()
                }

                Lifecycle.Event.ON_STOP -> {
                    unregister()
                }

                else -> {}
            }

        }

        private val intentFilter by lazy {
            IntentFilter().apply {
                Action.entries.forEach {
                    addAction(it.key)
                }
            }
        }

        init {
            log.i("init")
            activity.lifecycle.addObserver(lifecycleObserver)
            if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                register()
            }
        }

        private fun onActionCallback(action: Action) {
            log.i("onActionCallback: $action")
            onPipBroadcast(action)
        }

        private fun register() {
            log.i("register")
            activityRef.get()?.let {
                log.i("register: to $it")
                ContextCompat.registerReceiver(
                    it,
                    broadcastWrapper,
                    intentFilter,
                    ContextCompat.RECEIVER_EXPORTED
                )
            }
        }

        private fun unregister() {
            log.i("unregister")
            activityRef.get()?.unregisterReceiver(broadcastWrapper)
        }

    }

}