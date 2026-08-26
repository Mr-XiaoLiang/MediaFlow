package com.lollipop.common.tools

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

object Tasks {

    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val mainHandler by lazy {
        Handler(Looper.getMainLooper())
    }

    fun launch(
        context: CoroutineContext = Dispatchers.Main,
        block: suspend CoroutineScope.() -> Unit
    ) {
        scope.launch(context, block = block)
    }

}

fun Runnable.delay(delayMillis: Long) {
    Tasks.mainHandler.postDelayed(this, delayMillis)
}

fun Runnable.cancelDelay() {
    Tasks.mainHandler.removeCallbacks(this)
}

fun postUI(block: Runnable) {
    Tasks.mainHandler.post(block)
}
