package com.lollipop.mediaflow.tools

import android.database.Cursor

fun Cursor.optString(
    column: String,
    def: String = ""
): String {
    val cursor = this
    val index = cursor.getColumnIndex(column)
    if (index < 0) {
        return def
    }
    return cursor.getString(index) ?: def
}

fun Cursor.optLong(
    column: String,
    def: Long = 0L
): Long {
    val cursor = this
    val index = cursor.getColumnIndex(column)
    if (index < 0) {
        return def
    }
    return cursor.getLong(index)
}

fun Cursor.optInt(
    column: String,
    def: Int = 0
): Int {
    val cursor = this
    val index = cursor.getColumnIndex(column)
    if (index < 0) {
        return def
    }
    return cursor.getInt(index)
}

fun Cursor.optString(
    column: CursorColumn,
    def: String = ""
): String {
    return optString(column.key, def)
}

fun Cursor.optLong(
    column: CursorColumn,
    def: Long = 0L
): Long {
    return optLong(column.key, def)
}

fun Cursor.optInt(
    column: CursorColumn,
    def: Int = 0
): Int {
    return optInt(column.key, def)
}

interface CursorColumn {
    val key: String
}
