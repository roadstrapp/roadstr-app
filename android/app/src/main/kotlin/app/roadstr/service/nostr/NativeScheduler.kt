package app.roadstr.service.nostr

import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

fun interface NativeScheduledTask {
    fun cancel()
}

/** One-shot timers, injectable so the services are testable without sleeping. */
fun interface NativeScheduler {
    fun schedule(delayMillis: Long, task: () -> Unit): NativeScheduledTask
}

/** Daemon single-thread scheduler used in the app. */
class ExecutorNativeScheduler : NativeScheduler {
    private val executor = ScheduledThreadPoolExecutor(1) { runnable ->
        Thread(runnable, "roadstr-relay-timer").apply { isDaemon = true }
    }.apply { removeOnCancelPolicy = true }

    override fun schedule(delayMillis: Long, task: () -> Unit): NativeScheduledTask {
        val future: ScheduledFuture<*> = executor.schedule(
            { runCatching(task) },
            delayMillis.coerceAtLeast(0L),
            TimeUnit.MILLISECONDS,
        )
        return NativeScheduledTask { future.cancel(false) }
    }

    fun shutdown() {
        executor.shutdownNow()
    }
}
