package com.axacat.workflow.core

import androidx.annotation.Keep

sealed class Result<out T> {
    companion object {
        fun <T> success(value: T): Result<T> =
            Success(value)

        fun <T> failure(throwable: Throwable, value: T? = null): Result<T> =
            Failure(throwable, value)

        fun <T, R> wrap(result: Result<T>, transformer: (T) -> R): Result<R> {
            return try {
                when (result) {
                    is Success -> success(transformer(result.get()))
                    is Failure -> failure(result.exception(), result.getOrNull()?.let(transformer))
                }
            } catch (error: Throwable) {
                failure(error)
            }
        }
    }

    @Keep
    data class Success<out T>(
        val value: T
    ) : Result<T>() {
        fun get(): T = value
    }

    @Keep
    data class Failure<out T>(
        val throwable: Throwable,
        val value: T? = null
    ) : Result<T>() {
        fun exception(): Throwable = throwable

        override fun getOrNull(): T? = value
    }

    val isSuccess: Boolean
        get() = this is Success

    val isFailure: Boolean
        get() = this is Failure

    open fun getOrNull(): T? {
        return if (this is Success) {
            this.get()
        } else null
    }

    fun exceptionOrNull(): Throwable? {
        return if (this is Failure) {
            this.exception()
        } else null
    }
}