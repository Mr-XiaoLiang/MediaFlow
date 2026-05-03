package com.lollipop.common.tools

import java.io.Closeable

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

    fun getOrNull(): T? {
        if (this is Success) {
            return data
        }
        return null
    }

    fun errorOrNull(): Throwable? {
        if (this is Failure) {
            return error
        }
        return null
    }

}

inline fun <reified T> safeRun(block: () -> T): QuickResult<T> {
    return try {
        QuickResult.Success(block())
    } catch (e: Throwable) {
        QuickResult.Failure(e)
    }
}

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

inline fun <reified T : Closeable, reified R> QuickResult<T>.use(block: (T) -> R): QuickResult<R> {
    return mapValue { data ->
        data.use(block)
    }
}

