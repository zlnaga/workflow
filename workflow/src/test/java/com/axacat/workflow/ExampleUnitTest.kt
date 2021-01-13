package com.axacat.workflow

import com.axacat.workflow.core.ThreadOn
import com.axacat.workflow.core.node.BroadcastNode
import org.junit.Test

import org.junit.Assert.*
import java.util.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun test() {
        val broadcastNode = object : BroadcastNode(UUID.randomUUID().toString(), "test", ThreadOn.UNSPECIFIC) {
            override fun canHandle(input: Any): Boolean {
                return true
            }
        }

        broadcastNode.broadcast {
            println("AHA ! >> $it")
        }

        broadcastNode.accept("sss")
    }
}