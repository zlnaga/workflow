package com.axacat.workflow.core.node

import com.axacat.workflow.core.tracker.Tracker
import com.axacat.workflow.core.usecase.ConditionUseCase
import com.axacat.workflow.core.usecase.UseCase
import com.axacat.workflow.util.Logger
import com.axacat.workflow.core.Result
import com.axacat.workflow.core.usecase.AsyncUseCase

class ConditionNode<T, T1, T2, R1, R2>(
    uuid: String,
    key: String,
    private val source: ConditionUseCase<T>,
    private val case1: UseCase<T1, R1>,
    private val case2: UseCase<T2, R2>,
    private val inputTransformer1: ((T) -> T1)? = null,
    private val inputTransformer2: ((T) -> T2)? = null,
    private val tracker1: Tracker<T1, R1>? = null,
    private val tracker2: Tracker<T2, R2>? = null,
) : BroadcastNode(uuid, key, nextKeys = listOf(case1.key, case2.key)) {
    override fun canHandle(input: Any): Boolean {
        return source.canHandle(input)
    }

    override fun reset() {
        source.reset()
        case1.reset()
        case2.reset()
    }

    override fun onNext(pass: Result<*>) {
        Logger.d(tag = "Node_Trace[$uuid]") { "${source.key} is detected" }
        Logger.d(tag = "Node_Trace[$uuid]") {
            when (pass) {
                is Result.Success -> "${case1.key} is done"
                is Result.Failure -> "${case2.key} is done"
            }
        }
        super.onNext(pass)
    }

    @Suppress("UNCHECKED_CAST", "NULLABLE_TYPE_PARAMETER_AGAINST_NOT_NULL_TYPE_PARAMETER")
    override suspend fun onAccept(input: Any) {
        super.onAccept(input)
        try {
            val detect = source.detect(input as T)
            if (detect) {
                try {
                    val param = inputTransformer1?.invoke(input) ?: (input as T1)
                    when (case1) {
                        is AsyncUseCase -> {
                            val result = try {
                                val output = case1.process(param)
                                Result.success(output)
                            } catch (error: Throwable) {
                                Result.failure(error)
                            }
                            tracker1?.track(uuid, param, result)
                            onNext(result)
                        }
                        is ConditionUseCase -> {
                            val result = Result.success(param)
                            onNext(result)
                        }
                    }
                } catch (error: Throwable) {
                    Logger.w(error = error)
                    onNext(Result.failure<R1>(error))
                }
            } else {
                try {
                    val param = inputTransformer2?.invoke(input) ?: (input as T2)
                    when (case2) {
                        is AsyncUseCase -> {
                            val result = try {
                                val output = case2.process(param)
                                Result.success(output)
                            } catch (error: Throwable) {
                                Result.failure(error)
                            }
                            tracker2?.track(uuid, param, result)
                            when (result) {
                                is Result.Success -> {
                                    onNext(Result.failure(AssertionError("Detect failure"), result.get()))
                                }
                                is Result.Failure -> {
                                    onNext(result)
                                }
                            }
                        }
                        is ConditionUseCase -> {
                            val result = Result.failure(AssertionError("Detect failure"), param)
                            onNext(result)
                        }
                    }
                } catch (error: Throwable) {
                    Logger.w(error = error)
                    onError(error)
                }
            }
        } catch (error: Throwable) {
            Logger.w(error = error)
            onError(error)
        }
    }

}