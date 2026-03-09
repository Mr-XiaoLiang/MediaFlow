package com.lollipop.mediaflow.tools

import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ThreadHelper {

    private val log by lazy {
        registerLog()
    }

    val globalScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun onIOError(error: Throwable) {
        log.e("doAsync error", error)
    }

    fun onUIError(error: Throwable) {
        log.e("onUI error", error)
    }

    class SafeRunnable(
        private val error: (Throwable) -> Unit,
        private val content: () -> Unit
    ) : Runnable {

        private var job: Job? = null

        override fun run() {
            try {
                content()
            } catch (e: Throwable) {
                error(e)
                log.e("doAsync error", e)
            }
        }

        /** 在 UI 线程执行任务 (Android 环境需 Dispatchers.Main) **/
        fun runOnUI() {
            globalScope.launch(Dispatchers.Main) {
                run()
            }
        }

        /** 在 IO 线程执行任务 **/
        fun runOnIO() {
            globalScope.launch(Dispatchers.IO) {
                run()
            }
        }

        fun delayOnUI(delayMillis: Long) {
            job = globalScope.launch(Dispatchers.Main) {
                delay(delayMillis)
                run()
            }
        }

        fun delayOnIO(delayMillis: Long) {
            job = globalScope.launch(Dispatchers.IO) {
                delay(delayMillis)
                run()
            }
        }

        /** 延迟执行 **/
        fun cancel() {
            runCatching {
                job?.cancel()
            }
        }

    }

}

fun doAsync(
    error: suspend CoroutineScope.(Throwable) -> Unit = { ThreadHelper.onIOError(it) },
    content: suspend CoroutineScope.() -> Unit
) {
    ThreadHelper.globalScope.launch(Dispatchers.IO) {
        try {
            content()
        } catch (e: Throwable) {
            error(e)
        }
    }
}

suspend fun onUI(
    error: suspend CoroutineScope.(Throwable) -> Unit = { ThreadHelper.onUIError(it) },
    content: suspend CoroutineScope.() -> Unit
) {
    withContext(Dispatchers.Main) {
        try {
            content()
        } catch (e: Throwable) {
            error(e)
        }
    }
}

fun postUI(
    error: suspend CoroutineScope.(Throwable) -> Unit = { ThreadHelper.onUIError(it) },
    content: suspend CoroutineScope.() -> Unit
) {
    ThreadHelper.globalScope.launch(Dispatchers.Main) {
        try {
            content()
        } catch (e: Throwable) {
            error(e)
        }
    }
}

fun task(
    error: (Throwable) -> Unit = { ThreadHelper.onUIError(it) },
    content: () -> Unit
): ThreadHelper.SafeRunnable {
    return ThreadHelper.SafeRunnable(error, content)
}
