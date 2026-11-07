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
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.data.MediaMetadata
import java.lang.ref.WeakReference

object PIPHelper {

    /**
     * 画中画宽高比的可用区间。
     *
     * 平台通用边界由 `PictureInPictureParams.Builder.setAspectRatio` 规定：自 API 26 起，
     * 宽高比必须落在 1:2.39 与 2.39:1 之间（含边界），即约 0.418410 至 2.390000，
     * 这是 CDD / CTS 对所有 Android 设备的统一要求，并非某台设备上报的特殊值。
     *
     * 系统侧读取 framework 资源 config_pictureInPictureMinAspectRatio /
     * config_pictureInPictureMaxAspectRatio 并按当前屏幕校验，厂商可通过资源覆盖调整，
     * 因此平台边界不宜直接用作提交值或判定阈值。这里主动内缩到 1:2.3 ~ 2.3:1，
     * 使 [sanitizeAspectRatio] 的输出始终远离平台边界，避开浮点临界与设备配置差异；
     * 2.3 相比 2.39 仅收窄约 3.8%，21:9（约 2.33）一类片源的比例差异约 1.3%。
     */
    private const val MAX_RATIO = 23f / 10f
    private const val MIN_RATIO = 10f / 23f

    /** 越界回退比例：与 [MAX_RATIO] / [MIN_RATIO] 同源，避免阈值与提交值不一致。 */
    private val maxRational by lazy {
        Rational(23, 10)
    }

    private val minRational by lazy {
        Rational(10, 23)
    }

    /** 尺寸无效时回退的 1:1 占位，与此前 100×100 占位保持一致。 */
    private val defaultRational by lazy {
        Rational(1, 1)
    }

    private val log by lazy {
        registerLog()
    }

    fun registerPipActions(activity: ComponentActivity, onPipBroadcast: (Action) -> Unit): Holder {
        return Holder(
            activity = activity,
            adapter = PipBroadcastAdapter(activity, onPipBroadcast)
        )
    }

    private fun buildParams(
        activity: Activity,
        metadata: MediaMetadata?,
        option: Option?
    ): PictureInPictureParams? {
        val width = metadata?.width ?: 100
        val height = metadata?.height ?: 100
        return if (metadata?.needRotate == true) {
            buildParams(activity = activity, width = height, height = width, option = option)
        } else {
            buildParams(activity = activity, width = width, height = height, option = option)
        }
    }

    private fun buildParams(
        activity: Activity,
        width: Int,
        height: Int,
        option: Option?
    ): PictureInPictureParams? {
        if (!Preferences.isPictureInPictureEnable.get()) {
            return null
        }
        // 1. 归一宽高比，防止极端比例与无效尺寸被系统侧拒绝
        val finalRatio = sanitizeAspectRatio(width, height)

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
        val builder = PictureInPictureParams.Builder()
        builder.setAspectRatio(finalRatio)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(true)
        }
        builder.setActions(actionList)
        return builder.build()
    }

    /**
     * 把元数据宽高归一为系统可接受的画中画比例。
     *
     * [MediaMetadata] 的宽高在解析失败时会回退为 0；0 / 0 得到 NaN，而 NaN 与任何值
     * 比较均为 false，会绕过上下限判断，最终把表示 NaN 的 `Rational(0, 0)` 交给系统，
     * 触发 "Aspect ratio is too extreme"。因此先校验尺寸有效性与比例有限性。
     *
     * 返回的 [Rational] 恒落在 [MIN_RATIO] 与 [MAX_RATIO] 之间，不会贴近平台边界。
     */
    private fun sanitizeAspectRatio(width: Int, height: Int): Rational {
        if (width <= 0 || height <= 0) {
            return defaultRational
        }
        val rawRatio = width.toFloat() / height.toFloat()
        if (rawRatio.isNaN() || rawRatio.isInfinite()) {
            return defaultRational
        }
        return when {
            rawRatio > MAX_RATIO -> maxRational
            rawRatio < MIN_RATIO -> minRational
            else -> Rational(width, height)
        }
    }

    fun isInPictureInPictureMode(activity: Activity): Boolean {
        return activity.isInPictureInPictureMode
    }

    class Holder(
        val activity: ComponentActivity,
        val adapter: PipBroadcastAdapter
    ) {

        private var lastParams: PictureInPictureParams? = null

        private fun setParams(params: PictureInPictureParams?) {
            lastParams = params
            if (params != null) {
                try {
                    activity.setPictureInPictureParams(params)
                } catch (e: RuntimeException) {
                    // 系统侧还可能因设备不支持画中画、比例被设备资源拒绝或
                    // 比例变更过于频繁而抛异常，此处只记录，不中断播放页面。
                    log.e("setPictureInPictureParams failed", e)
                }
            }
        }

        fun onPictureInPictureRequested(): Boolean {
            val params = lastParams ?: return false
            if (!Preferences.isPictureInPictureEnable.get()) {
                return true
            }
            return try {
                activity.enterPictureInPictureMode(params)
                true
            } catch (e: RuntimeException) {
                log.e("enterPictureInPictureMode failed", e)
                false
            }
        }

        fun setParams(metadata: MediaMetadata?, option: Option?) {
            runCatching {
                setParams(buildParams(activity, metadata, option))
            }
        }

        fun setParams(
            width: Int,
            height: Int,
            option: Option?
        ) {
            runCatching {
                setParams(buildParams(activity, width, height, option))
            }
        }

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