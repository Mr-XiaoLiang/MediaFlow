package com.lollipop.mediaflow.upgrade

import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.TaskResult
import com.lollipop.mediaflow.tools.mapValue
import com.lollipop.mediaflow.tools.onFailure
import com.lollipop.mediaflow.tools.safeRun
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.TimeZone


object GithubApiModel {

    private const val URL = "https://api.github.com/repos/Mr-XiaoLiang/MediaFlow/releases/latest"

    private const val ONE_DAY = 1000L * 60 * 60 * 24

    private val log by lazy {
        registerLog()
    }

    private var releaseInfoCache: TaskResult<GithubReleaseInfo>? = null
    private var lastUpdateTime = 0L

    private val httpClient by lazy {
        createHttpClient()
    }

    private fun createHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().build()
    }

    /**
     * 获取自然天的天数
     */
    private fun getDayNumber(time: Long): Long {
        return (time + TimeZone.getDefault().rawOffset) / ONE_DAY
    }

    internal suspend fun fetchToday(): TaskResult<GithubReleaseInfo> {
        log.i("fetchToday()")
        return withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val cache = releaseInfoCache
            if (cache == null) {
                val result = fetch()
                // 失败不写缓存：否则当天后续调用会一直拿到失败结果
                if (result is TaskResult.Success) {
                    releaseInfoCache = result
                    lastUpdateTime = now
                }
                log.i("fetchToday(), no cache, fetch new")
                return@withContext result
            }
            val currentDay = getDayNumber(now)
            val lastDay = getDayNumber(lastUpdateTime)
            if (currentDay > lastDay) {
                val result = fetch()
                if (result is TaskResult.Success) {
                    releaseInfoCache = result
                    lastUpdateTime = now
                }
                log.i("fetchToday(), cache time out, fetch new")
                return@withContext result
            } else {
                log.i("fetchToday(), use cache")
                return@withContext cache
            }
        }
    }

    internal suspend fun fetch(): TaskResult<GithubReleaseInfo> {
        // curl -L \
        //  -H "Accept: application/vnd.github+json" \
        //  -H "Authorization: Bearer <YOUR-TOKEN>" \
        //  -H "X-GitHub-Api-Version: 2026-03-10" \
        //  https://api.github.com/repos/OWNER/REPO/releases
        log.i("fetch()")
        return withContext(Dispatchers.IO) {
            safeRun {
                Request.Builder()
                    .get()
                    .url(URL)
                    .header("User-Agent", "MediaFlow")
                    .header("X-GitHub-Api-Version", "2026-03-10")
                    .header("Accept", "application/vnd.github+json")
                    .build()
            }
                .mapValue { httpClient.newCall(it) }
                .quickExecute()
                .stringBody()
                .jsonObjectResult()
                .mapValue { GithubReleaseInfo.parseLatest(it) }
                .onFailure {
                    log.i("fetch error: \n ${it.stackTraceToString()}")
                }
        }
    }

}

fun TaskResult<GithubReleaseInfo>.hasUpdate(versionCode: Int): Boolean {
    return mapValue { info ->
        info.versionCode > versionCode
    }.getOrNull() ?: false
}
