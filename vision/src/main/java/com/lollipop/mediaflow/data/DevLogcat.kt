package com.lollipop.mediaflow.data

import android.icu.text.SimpleDateFormat
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.lollipop.common.tools.LLog.Companion.registerLog
import java.util.Date
import java.util.Locale

object DevLogcat {

    private val timeFormat by lazy {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    }

    private val log by lazy {
        registerLog()
    }

    val logLines = SnapshotStateList<Line>()

    fun i(content: String) {
        addLine(Level.INFO, content)
        log.i(content)
    }

    fun w(content: String) {
        addLine(Level.WARN, content)
        log.w(content)
    }

    fun e(content: String, throwable: Throwable? = null) {
        if (throwable == null) {
            addLine(Level.ERROR, content)
            log.e(content)
        } else {
            addLine(
                Level.ERROR,
                "$content\n${throwable.message}\n${throwable.stackTraceToString()}"
            )
            log.e(content, throwable)
        }
    }

    private fun addLine(level: Level, content: String) {
        val time = System.currentTimeMillis()
        val timeValue = timeFormat.format(Date((time)))
        logLines.add(
            Line(
                time = timeValue,
                timeLong = time,
                level = level,
                content = content
            )
        )
    }

    class Line(
        val time: String,
        val timeLong: Long,
        val level: Level,
        val content: String
    ) {

        val lineValue: String by lazy {
            "$time ${level.value} $content"
        }

    }

    enum class Level(val value: String) {
        INFO("I"),
        WARN("W"),
        ERROR("E");
    }

}

val DL = DevLogcat
