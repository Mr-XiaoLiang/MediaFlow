package com.lollipop.mediaflow.upgrade

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.lollipop.mediaflow.tools.LLog
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.QuickResult
import com.lollipop.mediaflow.tools.doAsync
import kotlinx.coroutines.Job
import java.io.File

class UpgradeService : Service() {

    companion object {
        private const val PARAMS_URL = "params_url"
        private const val PARAMS_CANCEL_LAST = "params_cancel_last"

        fun start(context: Context, url: String, cancelLast: Boolean = false) {
            val intent = Intent(context, UpgradeService::class.java)
            intent.putExtra(PARAMS_URL, url)
            intent.putExtra(PARAMS_CANCEL_LAST, cancelLast)
            context.startForegroundService(intent)
        }
    }

    private val log by lazy {
        registerLog()
    }

    private var currentTask: Task? = null

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val url = it.getStringExtra(PARAMS_URL) ?: ""
            if (url.isNotEmpty()) {
                val cancelLast = it.getBooleanExtra(PARAMS_CANCEL_LAST, false)
                onDownloadCommand(url, cancelLast)
            }
        }
        return START_NOT_STICKY
    }

    private fun onDownloadCommand(url: String, cancelLast: Boolean) {
        val lastTask = currentTask
        if (lastTask != null) {
            if (lastTask.url == url) {
                return
            }
            lastTask.unbind(cancelLast)
        }
        val task = Task(url, log)
        val newJob = doAsync {
            val downloadResult = GithubApiModel.download(url, getDownloadFile(), task)
            when (downloadResult) {
                is QuickResult.Failure<*> -> {
                    onDownloadFailure(downloadResult.error)
                }

                is QuickResult.Success<File> -> {
                    onDownloadSuccess(downloadResult.data)
                }
            }
        }
        task.bind(newJob, ::onDownloadUpdate)
        currentTask = task
        updateNotification(0)
    }

    private fun getDownloadFile(): File {
        TODO()
    }

    private fun onDownloadUpdate(progress: Int) {
        updateNotification(progress)
        TODO()
    }

    private fun updateNotification(progress: Int) {
        TODO()
    }

    private fun onDownloadSuccess(file: File) {
        TODO()
    }

    private fun onDownloadFailure(error: Throwable) {
        TODO()
    }

    private class Task(
        val url: String,
        val log: LLog
    ) : GithubApiModel.DownloadProgressCallback {

        private var job: Job? = null
        private var updateCallback: ((Int) -> Unit)? = null

        private var lastUpdateTime = 0L

        fun bind(job: Job, callback: (Int) -> Unit) {
            this.job = job
            this.updateCallback = callback
        }

        fun unbind(cancel: Boolean) {
            updateCallback = null
            if (cancel) {
                try {
                    job?.cancel()
                } catch (e: Throwable) {
                    log.e("Task.unbind.cancel", e)
                }
            }
        }

        override fun onDownloadUpdate(progress: Int) {
            // 为空就不报了
            val callback = updateCallback ?: return
            // 看看现在时间，如果超过了300ms，那么就更新
            val now = System.currentTimeMillis()
            if (now - lastUpdateTime < 300) {
                return
            }
            // 记录一下时间
            lastUpdateTime = now
            callback.invoke(progress)
        }

    }

}