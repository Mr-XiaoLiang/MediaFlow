package com.lollipop.mediaflow.upgrade

import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.QuickResult
import com.lollipop.mediaflow.tools.mapValue
import com.lollipop.mediaflow.tools.safeRun
import com.lollipop.mediaflow.tools.use
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.buffer
import okio.sink
import java.io.File


object GithubApiModel {

    private const val URL = "https://api.github.com/repos/Mr-XiaoLiang/MediaFlow/releases/latest"

    private val log by lazy {
        registerLog()
    }

    private val httpClient by lazy {
        createHttpClient()
    }

    private fun createHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().build()
    }

    suspend fun fetch(): QuickResult<GithubReleaseInfo> {
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

    /**
     * 下载文件
     * @param onUpdate 这里的回调函数将会回传进度，范围是[0～100], 如果是-1表示不确定的进度
     */
    suspend fun download(
        url: String,
        outFile: File,
        progressCallback: DownloadProgressCallback
    ): QuickResult<File> {
        return withContext(Dispatchers.IO) {
            safeRun { Request.Builder().url(url).build() }
                .mapValue { httpClient.newCall(it) }
                .quickExecute()
                .use { response ->
                    writeToFile(response, outFile, progressCallback)
                    outFile
                }
        }
    }

    private fun writeToFile(
        response: Response,
        outFile: File,
        progressCallback: DownloadProgressCallback
    ) {
        val body = response.body
        val totalBytes = body.contentLength()
        val source = body.source()
        outFile.sink().buffer().use { sink ->
            var downloadedBytes = 0L
            val bufferSize = 8192L
            var lastUpdateProgress = -1
            progressCallback.onDownloadUpdate(lastUpdateProgress)
            while (true) {
                // 读取数据到 sink 的缓冲区
                val read = source.read(sink.buffer, bufferSize)
                if (read < 0) {
                    break
                }

                sink.emitCompleteSegments() // 写入磁盘
                downloadedBytes += read

                // 计算进度
                val progress = (downloadedBytes * 100F / totalBytes).toInt()
                // 只有进度变化时才更新通知，避免频繁刷新 UI
                if (progress != lastUpdateProgress) {
                    progressCallback.onDownloadUpdate(progress)
                    lastUpdateProgress = progress
                }
            }
        }
    }

    interface DownloadProgressCallback {
        fun onDownloadUpdate(progress: Int)
    }

}