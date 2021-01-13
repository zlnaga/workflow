package com.axacat.workflow.core

import com.axacat.workflow.util.DistinctMutableList
import com.axacat.workflow.util.Logger
import com.axacat.workflow.util.distinctMutableListOf

abstract class WorkFlow<T : WorkFlowParam> {
    var graph: FlowGraph? = null
        set(value) {
            doActualDetach(field)
            field = value
            onGraphUpdated()
        }
    private val attachedFlows = mutableMapOf<String, DistinctMutableList<WorkFlow<*>>>()

    protected abstract fun createFlow(param: T): FlowGraph

    fun reset() {
        graph?.reset()
    }

    fun prepare(param: T) {
        graph = createFlow(param)
    }

    inline fun <reified R> produce(resultOn: ThreadOn? = ThreadOn.MAIN, crossinline callback: (Result<R>) -> Unit) {
        Logger.d(tag = this::class.java.name) { "work flow produce" }
        val gh = graph
        if (gh != null) {
            gh.error {
                callback(Result.failure(it))
                reset()
            }
            gh.produce<R>(resultOn = resultOn) {
                callback.invoke(Result.success(it))
                reset()
            }
        } else {
            callback(Result.failure(Throwable("This work flow is not prepared yet! call prepare() before produce()")))
        }
    }

    inline fun <reified R> standby(resultOn: ThreadOn? = ThreadOn.MAIN, crossinline callback: (Result<R>) -> Unit) {
        Logger.d(tag = this::class.java.name) { "work flow standby" }
        val gh = graph
        if (gh != null) {
            gh.error {
                callback(Result.failure(it))
                reset()
            }
            gh.standby<R>(resultOn = resultOn) {
                callback.invoke(Result.success(it))
                reset()
            }
        } else {
            callback(Result.failure(Throwable("This work flow is not prepared yet! call prepare() before standby()")))
        }
    }

    fun attachTo(key: String, other: WorkFlow<*>) {
        attachedFlows[key]?.let {
            it += other
        } ?: let {
            attachedFlows[key] = distinctMutableListOf(other)
        }
        doActualAttach(graph)
    }

    private fun doActualDetach(old: FlowGraph?) {
        if (old != null) {
            attachedFlows.forEach { entry ->
                entry.value.forEach { other ->
                    other.graph?.detach(entry.key, old)
                }
            }
        }
    }

    private fun doActualAttach(current: FlowGraph?) {
        if (current != null) {
            attachedFlows.forEach { entry ->
                entry.value.forEach { other ->
                    other.graph?.attach(entry.key, current)
                }
            }
        }
    }

    private fun onGraphUpdated() {
        doActualAttach(graph)
    }
}