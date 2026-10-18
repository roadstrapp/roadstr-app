package app.roadstr.offlinestt

import android.util.Log

/**
 * Loads the native engine: the C++ runtime, the CPU probe, then the dot-product build when the CPU
 * reports it and the baseline Armv8-A build otherwise. Fails closed: if the probe is unavailable
 * the baseline build is used, because guessing dot-product support could kill the process.
 */
internal object NativeLibraries {
    private const val TAG = "OfflineStt"

    private var state: State = State.Unloaded

    private sealed interface State {
        data object Unloaded : State
        data class Loaded(val variant: String) : State
        data class Failed(val error: Throwable) : State
    }

    /** Whether the engine can run on this device. Cheap after the first call. */
    @Synchronized
    fun ensureLoaded(): Result<String> {
        when (val current = state) {
            is State.Loaded -> return Result.success(current.variant)
            is State.Failed -> return Result.failure(current.error)
            State.Unloaded -> Unit
        }
        return runCatching { loadAll() }
            .onSuccess { state = State.Loaded(it) }
            .onFailure { state = State.Failed(it) }
    }

    private fun loadAll(): String {
        check(android.os.Build.SUPPORTED_64_BIT_ABIS.contains("arm64-v8a")) {
            "the engine ships for arm64-v8a only"
        }
        loadOrWarn("c++_shared")
        val variant = if (probeSupportsDotprod()) "dotprod" else "armv8"
        Log.i(TAG, "loading the $variant build")
        System.loadLibrary("offlinestt_$variant")
        return variant
    }

    private fun probeSupportsDotprod(): Boolean = try {
        System.loadLibrary("offlinestt_cpu_probe")
        supportsDotprodNative()
    } catch (error: UnsatisfiedLinkError) {
        Log.w(TAG, "CPU probe unavailable; using the baseline Armv8-A build")
        false
    }

    private fun loadOrWarn(name: String) {
        try {
            System.loadLibrary(name)
        } catch (error: UnsatisfiedLinkError) {
            // The system linker may still resolve it as a dependency of the engine library.
            Log.w(TAG, "lib$name was not preloaded", error)
        }
    }

    @JvmStatic
    external fun supportsDotprodNative(): Boolean
}
