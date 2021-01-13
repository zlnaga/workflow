package com.axacat.workflow.core.node

import com.axacat.workflow.core.tracker.Tracker
import com.axacat.workflow.core.usecase.UseCase
import com.axacat.workflow.util.Logger
import com.axacat.workflow.core.Result
import com.axacat.workflow.core.ThreadOn
import com.axacat.workflow.core.usecase.AsyncUseCase
import com.axacat.workflow.core.usecase.ConditionUseCase
import kotlinx.coroutines.launch

class LinkableNode<T, R>(
    uuid: String,
    key: String,
    private val case: UseCase<T, R>,
    threadOn: ThreadOn,
    private val inputTransformer: ((Any) -> T)? = null,
    private val tracker: Tracker<T, R>? = null,
) : BroadcastNode(uuid, key, threadOn, nextKeys = listOf(case.key)) {
    override fun canHandle(input: Any): Boolean {
        return case.canHandle(input)
    }

    override fun reset() {
        case.reset()
    }

    override fun onNext(pass: Result<*>) {
        Logger.d(tag = "Node_Trace[$uuid]") { "${case.key} is done" }
        super.onNext(pass)
    }

    @Suppress("UNCHECKED_CAST", "NULLABLE_TYPE_PARAMETER_AGAINST_NOT_NULL_TYPE_PARAMETER")
    override suspend fun onAccept(input: Any) {
        super.onAccept(input)
        try {
            val param = inputTransformer?.invoke(input) ?: (input as T)
            when (case) {
                is AsyncUseCase -> runOnWorker {
                    val result = try {
                        val output = case.process(param)
                        Result.success(output)
                    } catch (error: Throwable) {
                        Result.failure(error)
                    }
                    tracker?.track(uuid, param, result)
                    onNext(result)
                }
                is ConditionUseCase -> {
                    val result = try {
                        Result.success(param as R)
                    } catch (error: Throwable) {
                        Result.failure(error)
                    }
                    tracker?.track(this.uuid, param, result)
                    onNext(result)
                }
            }
        } catch (error: Throwable) {
            Logger.w(error = error)
            val result = Result.failure<R>(error)
            tracker?.track(this.uuid, input as T, result)
            onError(error)
        }
    }
}