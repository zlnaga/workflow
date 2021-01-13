package com.axacat.workflow.sample

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.axacat.workflow.core.FlowGraph
import com.axacat.workflow.core.ThreadOn
import com.axacat.workflow.core.WorkFlow
import com.axacat.workflow.core.WorkFlowParam
import com.axacat.workflow.core.usecase.AsyncUseCase
import com.axacat.workflow.core.usecase.ConditionUseCase
import kotlinx.coroutines.*
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    data class TestWorkFlowParam(
        val foo: String,
        val bar: Int
    ) : WorkFlowParam()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val resultText = findViewById<TextView>(R.id.text_result)
        val startBtn = findViewById<Button>(R.id.btn_start)

        val workFlow = object : WorkFlow<TestWorkFlowParam>() {

            override fun createFlow(param: TestWorkFlowParam): FlowGraph {
                val toIntCase = object : AsyncUseCase<Any, Int>("To Int") {
                    override suspend fun onProcess(input: Any): Int {
                        return if (input is Int) {
                            input
                        } else {
                            (input.toString() + param.foo).length
                        }
                    }
                }

                val doubleCase = object : AsyncUseCase<Int, Int>("Double") {
                    override suspend fun onProcess(input: Int): Int {
                        return input + input
                    }

                }

                val delay1Case = object : AsyncUseCase<Int, Int>("Delay") {
                    override suspend fun onProcess(input: Int): Int = withContext(Dispatchers.IO) {
                        delay(1000L)
                        return@withContext input
                    }
                }

                val delayNCase = object : AsyncUseCase<Int, Int>("DelayN") {
                    override suspend fun onProcess(input: Int): Int = withContext(Dispatchers.IO) {
                        delay(3 * 1000L)
                        return@withContext input
                    }
                }

                val errorCase = object : AsyncUseCase<Int, String>("Test error") {
                    override suspend fun onProcess(input: Int): String {
                        throw Throwable("Test error")
                    }
                }

                val largerThan20Case = object : ConditionUseCase<Int>("Detect larger than 20") {
                    override fun onDetect(input: Int): Boolean {
                        return input > 20
                    }
                }

                val toStringCase = object : AsyncUseCase<Int, String>("To String") {
                    override suspend fun onProcess(input: Int): String {
                        return input.toString()
                    }

                }

                return FlowGraph.create(
                    uuid = UUID.randomUUID().toString(),
                    tag = "TestWorkFlow",
                    case = toIntCase,
                    rootInput = param.bar,
                    threadOn = ThreadOn.UNSPECIFIC,
                )
                    .link(toIntCase, largerThan20Case)
                    .condition(
                        source = largerThan20Case,
                        case1 = doubleCase,
                        case2 = delay1Case
                    )
                    .link(doubleCase, delayNCase)
                    .link(delay1Case, errorCase)
                    .link(delayNCase, toStringCase)
            }
        }

        startBtn.setOnClickListener {
            workFlow.prepare(TestWorkFlowParam(foo = "hello", bar = Random.nextInt(10, 30)))
            workFlow.produce<String>(ThreadOn.MAIN) {
                resultText.append(it.toString() + "\n")
            }
        }
        Log.d(this::class.java.name, "Hello sample")

        runnable = MyRunnable(startBtn, mainHandler)
        mainHandler.post(runnable!!)
    }

    override fun onDestroy() {
        runnable?.let {
            mainHandler.removeCallbacks(it)
        }
        super.onDestroy()
    }

    class MyRunnable(
        view: View,
        private val handler: Handler
    ): Runnable {
        private val ref = WeakReference(view)

        override fun run() {
            ref.get()?.let { view ->
                view.performClick()
                handler.postDelayed(this, 1000)
            }
        }
    }

    val mainHandler = Handler(Looper.getMainLooper())

    var runnable: MyRunnable? = null
}