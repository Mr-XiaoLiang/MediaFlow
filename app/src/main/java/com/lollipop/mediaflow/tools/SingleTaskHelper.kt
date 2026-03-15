package com.lollipop.mediaflow.tools

import android.os.Handler
import android.os.HandlerThread
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

class SingleTaskHelper(
    val name: String,
    val taskContent: suspend CoroutineScope.() -> Unit
) {

    private val log by lazy {
        registerLog(name)
    }

    private var handlerThread: HandlerThread? = null
    private var taskHandler: Handler? = null

    private val taskRunnable = Runnable {
        runBlocking {
            taskContent()
        }
    }

    private fun getHandler(callback: (Handler) -> Unit) {
        synchronized(this) {
            try {
                val handler = taskHandler
                if (handler != null) {
                    callback(handler)
                    return
                }
                val newThread = HandlerThread(name, android.os.Process.THREAD_PRIORITY_BACKGROUND)
                newThread.start()
                handlerThread = newThread
                val newHandler = Handler(newThread.looper)
                taskHandler = newHandler
                callback(newHandler)
            } catch (e: Throwable) {
                log.e("getHandler", e)
            }
        }
    }

    fun delay(time: Long, cancelPrevious: Boolean = true) {
        try {
            getHandler {
                if (cancelPrevious) {
                    it.removeCallbacks(taskRunnable)
                }
                if (time > 0) {
                    it.postDelayed(taskRunnable, time)
                } else {
                    it.post(taskRunnable)
                }
            }
        } catch (e: Throwable) {
            log.e("delay", e)
        }
    }

    fun destroy() {
        synchronized(this) {
            try {
                handlerThread?.quitSafely()
                taskHandler = null
                handlerThread = null
            } catch (e: Throwable) {
                log.e("destroy", e)
            }
        }
    }

}