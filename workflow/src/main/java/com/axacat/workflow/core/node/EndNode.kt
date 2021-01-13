package com.axacat.workflow.core.node

import com.axacat.workflow.core.FlowGraph
import com.axacat.workflow.core.ThreadOn
import com.axacat.workflow.util.Logger

class EndNode(
    uuid: String,
    threadOn: ThreadOn,
) : BroadcastNode(uuid, FlowGraph.END, threadOn) {
    override fun canHandle(input: Any): Boolean {
        return true
    }

    override suspend fun onAccept(input: Any) {
        super.onAccept(input)
        Logger.d(tag = "Node_Trace[$uuid]") { "End by $input" }
        if (input is Throwable) {
            onError(input)
        } else {
            onResult(input)
        }
    }
}