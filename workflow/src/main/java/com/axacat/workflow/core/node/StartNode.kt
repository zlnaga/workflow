package com.axacat.workflow.core.node

import android.annotation.SuppressLint
import com.axacat.workflow.core.FlowGraph
import com.axacat.workflow.core.tracker.Tracker
import com.axacat.workflow.core.usecase.AsyncUseCase
import com.axacat.workflow.util.Logger
import com.axacat.workflow.core.Result

class StartNode<T, R>(
        uuid: String,
        private val case: AsyncUseCase<T, R>,
        private val inputTransformer: ((Any?) -> T)? = null,
        private val tracker: Tracker<T, R>? = null
) : BroadcastNode(uuid, FlowGraph.START, nextKeys = listOf(case.key)) {
    override fun canHandle(input: Any): Boolean {
        val param = inputTransformer?.invoke(input) ?: input
        return case.canHandle(param)
    }

    override fun reset() {
        case.reset()
    }

    override fun onNext(pass: Result<*>) {
        Logger.d(tag = "Node_Trace[$uuid]") { "${case.key} is done" }
        super.onNext(pass)
    }

    @SuppressLint("CheckResult")
    @Suppress("UNCHECKED_CAST", "NULLABLE_TYPE_PARAMETER_AGAINST_NOT_NULL_TYPE_PARAMETER")
    override suspend fun onAccept(input: Any) {
        super.onAccept(input)
        Logger.d(tag = "Node_Trace[$uuid]") { "Start from $key" }
        val param = inputTransformer?.invoke(input) ?: (input as T)
        val result = try {
            val output = case.process(param)
            Result.success(output)
        } catch (error: Throwable) {
            Logger.w(error = error)
            Result.failure(error)
        }
        tracker?.track(uuid, param, result)
        onNext(result)
    }
}