package util

import android.util.Log
import com.freefjay.localshare.TAG
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.*

val threadLocalQueueFlag = ThreadLocal<Boolean?>()

class TaskQueue(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val queue = Channel<Job>(Channel.UNLIMITED)

    init {
        scope.launch() {
            for (job in queue) {
                job.join()
            }
        }
    }

    fun submit(
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend () -> Unit) {
        val job = scope.launch(context = context + threadLocalQueueFlag.asContextElement(true), start = CoroutineStart.LAZY) {
            block()
        }
        queue.trySend(job)
    }

    suspend fun <T> execute(
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend () -> T
    ): T {
        val flag = threadLocalQueueFlag.get()
        Log.i(TAG, "是否在队列中：${flag}")
        return if (flag == true) {
            block()
        } else {
            suspendCoroutine {
                this.submit(context) {
                    try {
                        it.resume(block())
                    } catch (e: Exception) {
                        it.resumeWithException(e)
                    }
                }
            }
        }
    }

    fun cancel() {
        queue.cancel()
        scope.cancel()
    }

    suspend fun isFinish(): Boolean {
        var isFinish = true
        for (job in this.queue) {
            if (!job.isCompleted) {
                isFinish = false
                break
            }
        }
        return isFinish
    }

}

val taskQueue = TaskQueue()