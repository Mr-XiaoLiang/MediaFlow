package com.lollipop.mediaflow.upgrade

import androidx.compose.runtime.mutableStateOf
import com.lollipop.mediaflow.BuildConfig
import com.lollipop.mediaflow.tools.TaskResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 应用更新中心：**唯一**的更新请求入口与状态来源。
 *
 * 首页与设置页不再各自维护状态、各自发请求，而是共同观察 [status]：
 *
 * - [checkIfNeeded]：仅在从未检查过（[UpdateStatus.Idle]）时自动发起，
 *   首页与设置页都可以安全调用，不会出现“首页一次、设置页又一次”；
 * - [refresh]：用户主动点击时强制刷新，绕过自然日缓存；
 * - 内部用 [Mutex] + 共享 [Deferred] 做 single-flight，并发调用只发一次 HTTP。
 *
 * 自然日缓存仍由 [GithubApiModel.fetchToday] 负责，本对象只负责语义、去重与状态发布。
 */
object AppUpdateCenter {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var inFlight: Deferred<TaskResult<GithubReleaseInfo>>? = null

    /** 当前更新状态。Compose 可直接 `by` 观察；写入来自 IO 线程也安全。 */
    val status = mutableStateOf<UpdateStatus>(UpdateStatus.Idle)

    /**
     * 自动检查：只在从未检查过时发起。
     *
     * 已有结果、正在检查或刚失败时直接返回，避免同一进程内重复请求。
     * 失败后如需重试，由设置页的手动刷新走 [refresh]。
     */
    fun checkIfNeeded() {
        if (status.value !is UpdateStatus.Idle) {
            return
        }
        scope.launch {
            performCheck(force = false)
        }
    }

    /** 手动刷新：强制走网络；若已有请求在途，则共享同一次请求。 */
    fun refresh() {
        if (status.value is UpdateStatus.Checking) {
            return
        }
        scope.launch {
            performCheck(force = true)
        }
    }

    private suspend fun performCheck(force: Boolean) {
        status.value = UpdateStatus.Checking
        status.value = fetchShared(force).toUpdateStatus()
    }

    /**
     * single-flight：同一时刻最多一个请求，所有调用者等待同一个 [Deferred]。
     *
     * 锁只用于创建 / 读取 Deferred，`await` 在锁外，避免把整个网络请求锁住。
     */
    private suspend fun fetchShared(force: Boolean): TaskResult<GithubReleaseInfo> {
        val deferred = mutex.withLock {
            inFlight ?: scope.async {
                if (force) {
                    GithubApiModel.fetch()
                } else {
                    GithubApiModel.fetchToday()
                }
            }.also { inFlight = it }
        }
        return try {
            deferred.await()
        } finally {
            mutex.withLock {
                if (inFlight === deferred) {
                    inFlight = null
                }
            }
        }
    }

    private fun TaskResult<GithubReleaseInfo>.toUpdateStatus(): UpdateStatus {
        return when (this) {
            is TaskResult.Failure -> UpdateStatus.Failed(error)
            is TaskResult.Success -> {
                val downloadUrl = data.assets.firstOrNull {
                    it.name.endsWith("apk", ignoreCase = true)
                }?.url ?: ""
                if (data.versionCode > BuildConfig.VERSION_CODE && downloadUrl.isNotEmpty()) {
                    UpdateStatus.Available(data, downloadUrl)
                } else {
                    UpdateStatus.UpToDate
                }
            }
        }
    }
}

/** 更新检查的对外状态。UI 只读，不自行拼装请求结果。 */
sealed interface UpdateStatus {

    /** 尚未检查。 */
    object Idle : UpdateStatus

    /** 请求进行中。 */
    object Checking : UpdateStatus

    /** 有新版本，且已找到可下载的 APK。 */
    data class Available(
        val release: GithubReleaseInfo,
        val downloadUrl: String
    ) : UpdateStatus

    /** 当前已是最新版本。 */
    object UpToDate : UpdateStatus

    /** 检查失败；保留异常便于日志排查。 */
    data class Failed(val error: Throwable?) : UpdateStatus
}
