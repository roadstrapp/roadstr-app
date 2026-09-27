package app.roadstr.migration

import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

enum class NativeMigrationRunnerState {
    Idle,
    Running,
    Terminal,
}

/**
 * Single-flight asynchronous boundary for the future startup owner.
 *
 * The executor is injected deliberately: Android production code can provide
 * its lifecycle-owned worker, while host tests can control execution without
 * sleeping. A failed run returns to [NativeMigrationRunnerState.Idle] so the
 * caller can retry after repairing the legacy/native condition.
 */
class NativeMigrationStartupRunner(
    private val runtime: NativeMigrationRuntime,
    private val executor: Executor,
) {
    private val state = AtomicReference(NativeMigrationRunnerState.Idle)

    fun state(): NativeMigrationRunnerState = state.get()

    /**
     * Queues at most one run at a time. The callback is invoked on [executor]
     * after the state has been updated. A duplicate request is ignored.
     */
    fun start(callback: (MigrationResult) -> Unit): Boolean {
        if (!state.compareAndSet(NativeMigrationRunnerState.Idle, NativeMigrationRunnerState.Running)) {
            return false
        }

        try {
            executor.execute {
                val result = try {
                    runtime.run()
                } catch (_: RuntimeException) {
                    MigrationResult(
                        outcome = MigrationOutcome.Failed,
                        reason = "Native migration runner failed",
                    )
                }
                state.set(
                    if (result.outcome == MigrationOutcome.Failed) {
                        NativeMigrationRunnerState.Idle
                    } else {
                        NativeMigrationRunnerState.Terminal
                    },
                )
                callback(result)
            }
            return true
        } catch (_: RuntimeException) {
            state.set(NativeMigrationRunnerState.Idle)
            callback(
                MigrationResult(
                    outcome = MigrationOutcome.Failed,
                    reason = "Native migration worker unavailable",
                ),
            )
            return false
        }
    }
}
