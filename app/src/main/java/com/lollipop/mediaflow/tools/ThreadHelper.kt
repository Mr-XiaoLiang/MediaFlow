package com.lollipop.mediaflow.tools

import android.os.Handler
import android.os.Looper
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import java.util.concurrent.Executors

object ThreadHelper {

    private val log by lazy {
        registerLog()
    }

    private val ioExecutor by lazy {
        Executors.newCachedThreadPool()
    }

    private val uiExecutor by lazy {
        Handler(Looper.getMainLooper())
    }

    fun onIOError(error: Throwable) {
        log.e("doAsync error", error)
    }

    fun onUIError(error: Throwable) {
        log.e("onUI error", error)
    }

    fun doAsync(runnable: SafeRunnable) {
        ioExecutor.execute(runnable)
    }

    fun onUI(runnable: SafeRunnable) {
        uiExecutor.post(runnable)
    }

    class SafeRunnable(
        private val error: (Throwable) -> Unit,
        private val content: () -> Unit
    ) : Runnable {
        override fun run() {
            try {
                content()
            } catch (e: Throwable) {
                error(e)
                log.e("doAsync error", e)
            }
        }

        fun runOnUI() {
            onUI(this)
        }

        fun runOnIO() {
            doAsync(this)
        }

        fun delay(delayMillis: Long) {
            uiExecutor.postDelayed(this, delayMillis)
        }

        fun cancel() {
            uiExecutor.removeCallbacks(this)
        }

    }

}

fun doAsync(
    error: (Throwable) -> Unit = { ThreadHelper.onIOError(it) },
    content: () -> Unit
) {
    ThreadHelper.doAsync(ThreadHelper.SafeRunnable(error, content))
}

fun onUI(
    error: (Throwable) -> Unit = { ThreadHelper.onUIError(it) },
    content: () -> Unit
) {
    ThreadHelper.onUI(ThreadHelper.SafeRunnable(error, content))
}

fun task(
    error: (Throwable) -> Unit = { ThreadHelper.onUIError(it) },
    content: () -> Unit
): ThreadHelper.SafeRunnable {
    return ThreadHelper.SafeRunnable(error, content)
}
