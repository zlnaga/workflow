package com.axacat.workflow.core.node

import androidx.annotation.CallSuper
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow

abstract class BroadcastNode(
        uuid: String,
        key: String,
        nextKeys: List<String>? = null
) : Node(uuid, key, nextKeys) {
    private val broadcaster = Channel<Any>(Channel.UNLIMITED)
    private var broadcastReceiver: ((Any) -> Unit)? = null

    init {
        runOnMain {
            broadcaster.consumeAsFlow().catch { error ->
                onError(error)
            }.collect {
                try {
                    broadcastReceiver?.invoke(it)
                } catch (error: Throwable) {
                    onError(error)
                }
            }
        }
    }

    override fun canHandle(input: Any): Boolean {
        return true
    }

    @CallSuper
    override suspend fun onAccept(input: Any) {
        try {
            if (canHandle(input)) {
                broadcaster.send(input)
            }
        } catch (error: Throwable) {
            onError(error)
        }
    }

    fun broadcast(handler: (Any) -> Unit) {
        broadcastReceiver = handler
    }
}