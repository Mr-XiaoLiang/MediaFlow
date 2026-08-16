package com.lollipop.common.tools

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object CrashHelper {

    private const val DIR_NAME = "crash_reports"
    private const val JAVA_LOG_NAME = "crash_java.txt"
    private const val EXIT_REASONS_LOG_NAME = "exit_reasons.txt"

    fun register(application: Application) {
        Register(application)
    }

    fun reportDelegate(context: Context): CrashDelegate {
        return CrashDelegate(context)
    }

    private fun saveJavaCrash(context: Context, throwable: Throwable) {
        val dir = File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }
        val logFile = File(dir, JAVA_LOG_NAME)

        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val time = sdf.format(Date())

        val content = """
            ==================== JAVA / KOTLIN CRASH ====================
            Time: $time
            Device: ${Build.MANUFACTURER} ${Build.MODEL}
            Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
            -------------------------------------------------------------
            """.trimIndent()

        logFile.writeText(content + "\n" + sw)
    }

    private fun checkAndExportExitReasons(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val exitInfos = am.getHistoricalProcessExitReasons(context.packageName, 0, 1)
        if (exitInfos.isEmpty()) {
            return
        }

        val lastExit = exitInfos.first()

        if (lastExit.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) {
            val dir = File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }
            val nativeFile = File(dir, EXIT_REASONS_LOG_NAME)

            val traceContent = lastExit.traceInputStream?.bufferedReader()?.use { it.readText() }
                ?: "No Trace Stream available from System."

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val time = sdf.format(Date(lastExit.timestamp))

            val content = """
                ==================== SYSTEM EXIT REASONS ====================
                Time: $time
                Reason Code: REASON_CRASH_NATIVE
                Description: ${lastExit.description}
                Status: ${lastExit.status}
                -------------------------------------------------------------
                SYSTEM TOMBSTONE / TRACE LOG:
                """.trimIndent()

            nativeFile.writeText(content + "\n" + traceContent)
        }
    }

    private fun hasCrashLog(context: Context): Boolean {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) {
            return false
        }
        val logSize = dir.list()?.size ?: return false
        return logSize > 0
    }

    private fun createCrashLogShareIntent(context: Context): Intent? {
        val dir = File(context.filesDir, DIR_NAME)
        val filesToZip = dir.listFiles() ?: return null
        if (filesToZip.isEmpty()) {
            return null
        }

        // 1. 打包为 Zip 文件
        val zipFile = File(context.cacheDir, "crash_report_${System.currentTimeMillis()}.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            for (file in filesToZip) {
                zos.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { input -> input.copyTo(zos) }
                zos.closeEntry()
            }
        }

        // 2. 通过 FileProvider 生成可分享的 Uri
        val authority = "${context.packageName}.crash.fileprovider"
        val zipUri: Uri = FileProvider.getUriForFile(context, authority, zipFile)

        // 3. 构建通用 Intent.ACTION_SEND
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip" // 或者是 "application/octet-stream"
            putExtra(Intent.EXTRA_SUBJECT, "[Crash Report] ${context.packageName} 崩溃日志")
            putExtra(Intent.EXTRA_TEXT, "这是应用上次异常退出的日志压缩包（内置系统与运行堆栈）。")
            putExtra(Intent.EXTRA_STREAM, zipUri)
            // 授予临时读取权限
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return shareIntent
    }

    private fun shareCrash(context: Context, shareIntent: Intent) {
        // 4. 唤起系统底层原生的“分享”选择弹窗
        try {
            val chooser = Intent.createChooser(shareIntent, "Share Crash Log")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun clearLogs(context: Context) {
        val dir = File(context.filesDir, DIR_NAME)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    private class Register(private val context: Context) : Thread.UncaughtExceptionHandler {

        private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        init {
            Thread.setDefaultUncaughtExceptionHandler(this)
        }

        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            try {
                // 写入 crash_java.txt
                saveJavaCrash(context, throwable)
            } finally {
                // 将控制权交还给系统杀死进程
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

    }

    class CrashDelegate(private val context: Context) {

        private val isLoadingState = mutableStateOf(false)
        private var hasCrashLogState = mutableStateOf(false)

        val isLoading: State<Boolean>
            get() {
                return isLoadingState
            }

        val hasCrashLog: State<Boolean>
            get() {
                return hasCrashLogState
            }

        suspend fun clearLogs() {
            isLoadingState.value = true
            withContext(Dispatchers.IO) {
                try {
                    clearLogs(context)
                } catch (e: Throwable) {
                    DL.e("clearLogs", e)
                }
                isLoadingState.value = false
                hasCrashLogState.value = hasCrashLog(context)
            }
        }

        suspend fun checkAndExportExitReasons() {
            isLoadingState.value = true
            withContext(Dispatchers.IO) {
                try {
                    checkAndExportExitReasons(context)
                } catch (e: Throwable) {
                    DL.e("checkAndExportExitReasons", e)
                }
                isLoadingState.value = false
                hasCrashLogState.value = hasCrashLog(context)
            }
        }

        suspend fun shareCrash() {
            isLoadingState.value = true
            withContext(Dispatchers.IO) {
                val shareIntent = try {
                    createCrashLogShareIntent(context)
                } catch (e: Throwable) {
                    DL.e("createCrashLogShareIntent", e)
                    null
                }
                if (shareIntent != null) {
                    withContext(Dispatchers.Main) {
                        try {
                            shareCrash(context, shareIntent)
                        } catch (e: Throwable) {
                            DL.e("shareCrash", e)
                        }
                    }
                }
                isLoadingState.value = false
            }
        }

    }

}