package com.axacat.workflow.core.node

import com.axacat.workflow.core.Result
import androidx.annotation.CallSuper
import com.axacat.workflow.core.ThreadOn
import com.axacat.workflow.util.Logger
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

abstract class Node(
    val uuid: String,
    val key: String,
    private val threadOn: ThreadOn? = ThreadOn.UNSPECIFIC,
    val nextKeys: List<String>? = null
) {
    private val default by lazy { CoroutineScope(Dispatchers.Default) }

    private val worker by lazy {
        when (threadOn) {
            ThreadOn.MAIN -> CoroutineScope(Dispatchers.Main)
            ThreadOn.IO -> CoroutineScope(Dispatchers.IO)
            ThreadOn.UNSPECIFIC -> CoroutineScope(Dispatchers.Default)
            else -> null
        }
    }

    private var errorHandler: ((Throwable) -> Unit)? = null
    var resultHandler: ((Any?) -> Unit)? = null

    private var next: ((Result<*>) -> Node?)? = null

    protected fun runOnWorker(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit
    ) = (worker ?: default).launch(context, start, block)

    abstract fun canHandle(input: Any): Boolean

    abstract suspend fun onAccept(input: Any)

    open fun reset() {}

    fun accept(input: Any) = runOnWorker {
        onAccept(input)
    }

    @CallSuper
    open fun onNext(pass: Result<*>) {
        runOnWorker {
            Logger.d(tag = "Node_Trace[$uuid]") { "Actual running thread" }
            val nextNode = next?.invoke(pass)
            if (nextNode != null) {
                this@Node.result(nextNode::onResult)
                when (pass) {
                    is Result.Success -> nextNode.accept(pass.get() as Any)
                    is Result.Failure -> {
                        val data = pass.getOrNull()
                        val error = pass.exception()
                        when {
                            data != null -> nextNode.accept(data)
                            nextNode.canHandle(error) -> nextNode.accept(error)
                            else -> nextNode.onError(error)
                        }
                    }
                }
            } else {
                when (pass) {
                    is Result.Success -> onResult(pass.get())
                    is Result.Failure -> onError(pass.exception())
                }
            }
        }
    }

    fun onResult(ret: Any?) {
        resultHandler?.invoke(ret)
    }

    fun onError(error: Throwable) {
        errorHandler?.invoke(error)
    }

    fun next(factory: (Result<*>) -> Node?) {
        this.next = { result ->
            try {
                factory.invoke(result)
            } catch (error: Throwable) {
                onError(error)
                null
            }
        }
    }

    inline fun <reified T> result(crossinline handler: (T) -> Unit) {
        resultHandler = { ret ->
            if (ret is T) {
                handler(ret)
            } else {
                onError(Throwable("Result type is mismatched!! >> Expect[${T::class.java.name}] but got[${ret?.let { it::class.java.name }}]"))
            }
        }
    }

    fun error(handler: (Throwable) -> Unit) {
        errorHandler = handler
    }
}