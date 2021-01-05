package com.axacat.workflow.core.tracker

import com.axacat.workflow.core.Result

fun interface Tracker<T, R> {
    fun track(uuid: String, input: T, output: Result<R>)
}