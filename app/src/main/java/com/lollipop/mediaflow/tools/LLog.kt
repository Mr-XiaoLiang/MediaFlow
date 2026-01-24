package com.lollipop.mediaflow.tools

import android.util.Log

class LLog(private val tag: String) {

    companion object {
        private const val TAG = "Lollipop"
        inline fun <reified T : Any> T.registerLog(tag: String = ""): LLog {
            val obj = this
            val className = obj.javaClass.simpleName
            val hash = System.identityHashCode(obj)
            if (tag.isEmpty()) {
                return LLog("${className}@${hash}")
            }
            return LLog("$tag#${className}@${hash}")
        }

        val isDebug: Boolean = BuildConfig.DEBUG

    }

    fun d(msg: String) {
        if (isDebug) {
            Log.d(TAG, "$tag: $msg")
        }
    }

    fun e(msg: String, throwable: Throwable? = null) {
        Log.e(TAG, "$tag: $msg", throwable)
    }

    fun i(msg: String) {
        if (isDebug) {
            Log.i(TAG, "$tag: $msg")
        }
    }

    fun w(msg: String) {
        if (isDebug) {
            Log.w(TAG, "$tag: $msg")
        }
    }


}