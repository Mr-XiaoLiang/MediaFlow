package com.lollipop.common.tools

import com.lollipop.common.tools.TaskResult.Failure
import com.lollipop.common.tools.TaskResult.Success
import java.io.Closeable

sealed class TaskResult<out T : Any> {

    class Success<T : Any>(val data: T) : TaskResult<T>()
    class Failure(val error: Throwable) : TaskResult<Nothing>()

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

inline fun <T : Any> safeRun(block: () -> T): TaskResult<T> {
    return try {
        Success(block())
    } catch (e: Throwable) {
        Failure(e)
    }
}

inline fun <T : Any> safeNoNull(block: () -> T?): TaskResult<T> {
    return try {
        val data = block() ?: return Failure(NullPointerException("data is null"))
        Success(data)
    } catch (e: Throwable) {
        Failure(e)
    }
}

inline fun <reified T : Any> TaskResult<T>.onSuccess(callback: (T) -> Unit): TaskResult<T> {
    if (this is Success) {
        callback(data)
    }
    return this
}

inline fun <reified T : Any> TaskResult<T>.onFailure(callback: (Throwable) -> Unit): TaskResult<T> {
    if (this is Failure) {
        callback(error)
    }
    return this
}

inline fun <reified T : Any, reified R : Any> TaskResult<T>.mapTo(block: (T) -> TaskResult<R>): TaskResult<R> {
    val from = this
    return try {
        when (from) {
            is Failure -> {
                Failure(from.error)
            }

            is Success -> {
                block(from.data)
            }
        }
    } catch (e: Throwable) {
        Failure(e)
    }
}

inline fun <reified T : Any, reified R : Any> TaskResult<T>.mapValue(block: (T) -> R): TaskResult<R> {
    return mapTo { Success(block(it)) }
}

inline fun <reified T : Any, reified R : Any> TaskResult<T>.mapValueNoNull(block: (T) -> R?): TaskResult<R> {
    return mapTo { safeNoNull { block(it) } }
}


inline fun <reified T : Closeable, reified R : Any> TaskResult<T>.use(block: (T) -> R): TaskResult<R> {
    return mapValue { data ->
        data.use(block)
    }
}

inline fun <reified T : Closeable, reified R : Any> TaskResult<T>.useNoNull(block: (T) -> R?): TaskResult<R> {
    return mapTo { data ->
        safeNoNull { data.use(block) }
    }
}

