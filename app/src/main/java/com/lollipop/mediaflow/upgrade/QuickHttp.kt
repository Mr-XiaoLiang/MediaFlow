package com.lollipop.mediaflow.upgrade

import okhttp3.Call
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable

inline fun <reified T, reified R> QuickResult<T>.mapTo(block: (T) -> QuickResult<R>): QuickResult<R> {
    val from = this
    return try {
        when (from) {
            is QuickResult.Failure<T> -> {
                QuickResult.Failure<R>(from.error)
            }

            is QuickResult.Success<T> -> {
                block(from.data)
            }
        }
    } catch (e: Throwable) {
        QuickResult.Failure(e)
    }
}

inline fun <reified T, reified R> QuickResult<T>.mapValue(block: (T) -> R): QuickResult<R> {
    return mapTo { QuickResult.Success(block(it)) }
}

inline fun <reified T> quick(from: T): QuickResult<T> {
    return QuickResult.Success(from)
}

fun QuickResult<Call>.quickExecute(): QuickResult<Response> {
    return mapValue { it.execute() }
}

fun QuickResult<Response>.stringBody(): QuickResult<String> {
    return mapTo { response ->
        if (response.code == 200) {
            QuickResult.Success(response.body.string())
        } else {
            QuickResult.Failure(HttpException(response.code, response.message))
        }
    }
}

fun QuickResult<String>.jsonObjectResult(): QuickResult<JSONObject> {
    return mapValue { response ->
        JSONObject(response)
    }
}

fun QuickResult<String>.jsonArrayResult(): QuickResult<JSONArray> {
    return mapValue { response ->
        JSONArray(response)
    }
}

inline fun <reified T : Closeable, reified R> QuickResult<T>.use(block: (T) -> R): QuickResult<R> {
    return mapValue { data ->
        data.use(block)
    }
}


sealed class QuickResult<T> {

    class Success<T>(val data: T) : QuickResult<T>()
    class Failure<T>(val error: Throwable) : QuickResult<T>()

    fun onSuccess(callback: (T) -> Unit): QuickResult<T> {
        if (this is Success) {
            callback(data)
        }
        return this
    }

    fun onFailure(callback: (Throwable) -> Unit): QuickResult<T> {
        if (this is Failure) {
            callback(error)
        }
        return this
    }

}

class HttpException(val code: Int, val msg: String) : RuntimeException("code: $code, msg: $msg")
