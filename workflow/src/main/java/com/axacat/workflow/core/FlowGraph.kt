package com.axacat.workflow.core

import com.axacat.workflow.core.node.*
import com.axacat.workflow.core.tracker.Tracker
import com.axacat.workflow.core.usecase.AsyncUseCase
import com.axacat.workflow.core.usecase.ConditionUseCase
import com.axacat.workflow.core.usecase.UseCase
import com.axacat.workflow.util.DistinctMutableList
import com.axacat.workflow.util.Logger
import com.axacat.workflow.util.distinctMutableListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FlowGraph private constructor(
    val uuid: String,
    private val tag: String,
    val rootInput: Any = START,
    val threadOn: ThreadOn = ThreadOn.UNSPECIFIC
) {
    private val attachedGraph = mutableMapOf<String, DistinctMutableList<FlowGraph>>()
    val router = mutableMapOf<String, Node>()
    private var errorHandler: ((Throwable) -> Unit)? = null

    var root: Node? = null
    val endNode by lazy {
        EndNode(uuid, threadOn)
    }

    companion object {
        const val START = "FlowGraph_START_SIGNAL"
        const val END = "FlowGraph_END_SIGNAL"

        fun <T, R> create(
            uuid: String,
            tag: String,
            case: AsyncUseCase<T, R>,
            rootInput: Any = START,
            threadOn: ThreadOn = ThreadOn.UNSPECIFIC,
            inputTransformer: ((Any?) -> T)? = null,
            tracker: Tracker<T, R>? = null
        ): FlowGraph {
            val graph = FlowGraph(uuid, tag, rootInput, threadOn)
            val node = StartNode(
                uuid = uuid,
                case = case,
                threadOn = threadOn,
                inputTransformer = inputTransformer,
                tracker = tracker
            )
            node.next {
                graph.router[case.key]
            }
            graph.add(node)
            return graph
        }
    }

    private fun onEndByError(error: Throwable) {
        endNode.accept(error)
    }

    fun onError(error: Throwable) {
        errorHandler?.invoke(error)
    }

    fun error(handler: (Throwable) -> Unit) {
        errorHandler = handler
    }

    fun attach(key: String, graph: FlowGraph) {
        attachedGraph[key]?.let {
            it += graph
        } ?: let {
            attachedGraph[key] = distinctMutableListOf(graph)
        }
    }

    fun detach(key: String, graph: FlowGraph) {
        attachedGraph[key]?.let {
            it -= graph
        }
    }

    fun dispatch(key: String, input: Any = START) {
        attachedGraph[key]?.forEach { graph ->
            Logger.d { "Found attached graph with dispatch signal >> $key" }
            graph.start(input)
        }
    }

    fun add(node: Node): FlowGraph {
        router[node.key] = node
        node.nextKeys?.forEach { nextKey ->
            if (!router.containsKey(nextKey)) {
                router[nextKey] = endNode
            }
        }
        node.error {
            Logger.w(error = it)
            onEndByError(it)
        }
        if (node is BroadcastNode) {
            node.broadcast {
                dispatch(node.key, it)
            }
        }
        return this
    }

    inline fun <T1, reified R1, T2, reified R2> link(
        case1: UseCase<T1, R1>,
        case2: UseCase<T2, R2>,
        threadOn: ThreadOn? = null,
        noinline inputTransformer: ((R1) -> T2)? = null,
        tracker: Tracker<T2, R2>? = null
    ): FlowGraph {
        val node = LinkableNode(
            uuid = uuid,
            key = case1.key,
            case = case2,
            threadOn = threadOn ?: this.threadOn,
            inputTransformer = {
                @Suppress("UNCHECKED_CAST")
                inputTransformer?.invoke(it as R1) ?: (it as T2)
            },
            tracker = tracker
        )
        when (case2) {
            is AsyncUseCase -> {
                node.next {
                    router[case2.key]
                }
            }
            is ConditionUseCase -> {
                node.next {
                    router[case2.key]
                }
            }
        }
        return add(node)
    }

    inline fun <reified T, T1, T2, reified R1, reified R2> condition(
        source: ConditionUseCase<T>,
        case1: UseCase<T1, R1>,
        case2: UseCase<T2, R2>,
        threadOn: ThreadOn? = null,
        noinline inputTransformer1: ((T) -> T1)? = null,
        noinline inputTransformer2: ((T) -> T2)? = null,
        tracker1: Tracker<T1, R1>? = null,
        tracker2: Tracker<T2, R2>? = null
    ): FlowGraph {
        val node = ConditionNode(
            uuid = uuid,
            key = source.key,
            source = source,
            case1 = case1,
            case2 = case2,
            threadOn = threadOn ?: this.threadOn,
            inputTransformer1 = inputTransformer1,
            inputTransformer2 = inputTransformer2,
            tracker1 = tracker1,
            tracker2 = tracker2
        )
        node.next {
            if (it is Result.Success) {
                router[case1.key]
            } else {
                router[case2.key]
            }
        }
        return add(node)
    }

    inline fun <reified T> produce(input: Any = rootInput, resultOn: ThreadOn? = ThreadOn.MAIN, crossinline handler: (T) -> Unit) {
        root = router[START]
        val scope = when (resultOn) {
            ThreadOn.MAIN -> CoroutineScope(Dispatchers.Main)
            ThreadOn.IO -> CoroutineScope(Dispatchers.IO)
            ThreadOn.UNSPECIFIC -> CoroutineScope(Dispatchers.Default)
            else -> CoroutineScope(Dispatchers.Unconfined)
        }
        endNode.result<T> {
            scope.launch {
                Logger.d(tag = "Node_Trace[$uuid]") { "produce result >> $it" }
                handler.invoke(it)
            }
        }
        endNode.error {
            scope.launch {
                Logger.d(tag = "Node_Trace[$uuid]") { "handle error >> $it" }
                onError(it)
            }
        }
        endNode.broadcast {
            dispatch(END, it)
        }
        start(input)
    }

    inline fun <reified T> standby(resultOn: ThreadOn? = ThreadOn.MAIN, crossinline handler: (T) -> Unit) {
        root = router[START]
        val scope = when (resultOn) {
            ThreadOn.MAIN -> CoroutineScope(Dispatchers.Main)
            ThreadOn.IO -> CoroutineScope(Dispatchers.IO)
            ThreadOn.UNSPECIFIC -> CoroutineScope(Dispatchers.Default)
            else -> CoroutineScope(Dispatchers.Unconfined)
        }
        endNode.result<T> {
            scope.launch {
                Logger.d(tag = "Node_Trace[$uuid]") { "produce result >> $it" }
                handler.invoke(it)
            }
        }
        endNode.error {
            scope.launch {
                Logger.d(tag = "Node_Trace[$uuid]") { "handle error >> $it" }
                onError(it)
            }
        }
        endNode.broadcast {
            dispatch(END, it)
        }
    }

    fun start(input: Any = rootInput) {
        Logger.d("Node_Trace[$uuid]") { "Start Flow[$tag]" }
        root?.accept(input)
    }

    fun reset() {
        router.values.forEach { node ->
            node.reset()
        }
    }
}