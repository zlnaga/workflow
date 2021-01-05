package com.axacat.workflow.util

import android.util.Log
import com.axacat.workflow.BuildConfig

object Logger {
    const val DEFAULT_TAG = "Workflow"

    var loggerLevel = {
        if (BuildConfig.DEBUG) {
            Log.DEBUG
        } else {
            Log.ERROR
        }
    }

    var pathDepth = 0

    inline fun d(tag: String = DEFAULT_TAG, message: () -> Any) {
        if (Log.DEBUG >= loggerLevel()) {
            log(Log.DEBUG, tag, message())
        }
    }

    inline fun i(tag: String = DEFAULT_TAG, message: () -> Any) {
        if (Log.INFO >= loggerLevel()) {
            log(Log.INFO, tag, message())
        }
    }

    fun w(error: Throwable) {
        w(tag = DEFAULT_TAG, error = error)
    }

    fun w(tag: String = DEFAULT_TAG, error: Throwable) {
        if (Log.WARN >= loggerLevel()) {
            log(Log.WARN, tag, { error.message ?: error::class.java.name }, error)
        }
    }

    inline fun w(
        tag: String = DEFAULT_TAG,
        error: Throwable? = null,
        message: () -> Any? = { error?.message ?: error?.let { it::class.java.name } }
    ) {
        if (Log.WARN >= loggerLevel()) {
            log(Log.WARN, tag, message(), error)
        }
    }

    fun e(error: Throwable) {
        e(tag = DEFAULT_TAG, error = error)
    }

    fun e(tag: String = DEFAULT_TAG, error: Throwable) {
        if (Log.ERROR >= loggerLevel()) {
            log(Log.ERROR, tag, { error.message ?: error::class.java.name }, error)
        }
    }

    inline fun e(
        tag: String = DEFAULT_TAG,
        error: Throwable?,
        message: () -> Any? = { error?.message ?: error?.let { it::class.java.name } }
    ) {
        if (Log.ERROR >= loggerLevel()) {
            log(Log.ERROR, tag, message(), error)
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun log(level: Int, tag: String, message: Any?, error: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            Log.println(level, tag,"${
                if (message != null) {
                    "$message\n"
                } else {
                    ""
                }
            }${error?.message.orEmpty()}")
            // Show log path
            if (pathDepth > 0) {
                val stackTrace = if (error != null) {
                    error.stackTrace
                } else {
                    Throwable("Trace Mock Error").stackTrace.let {
                        it.copyOfRange(1, it.size)
                    }
                }
                error?.printStackTrace() ?: stackTrace?.let {
                    if (it.size > 2 + pathDepth) {
                        it.copyOfRange(2, 2 + pathDepth)
                    } else {
                        it
                    }
                }?.forEach {
                    Log.println(level, tag, String.format("\tat %s (%s:%s)", it.methodName, it.className, it.lineNumber))
                }
            }
        }
    }
}