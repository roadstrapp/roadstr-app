package app.roadstr.migration

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.it_nomads.fluttersecurestorage.FlutterSecureStoragePlugin
import io.flutter.FlutterInjector
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugins.pathprovider.PathProviderPlugin
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object LegacyMigrationChannelContract {
    const val protocolVersion = 1
    const val channelName = "app.roadstr/legacy_migration"
    const val readyMethod = "bridgeReady"
    const val readMethod = "readLegacySnapshot"
    const val entrypointLibrary = "package:roadstr/main.dart"
    const val entrypointFunction = "legacyMigrationHeadlessMain"
    const val failureCode = "legacy_snapshot_unavailable"
}

/**
 * Isolated Android transport for the temporary Dart migration reader.
 *
 * It deliberately disables generated plugin registration and installs only
 * path_provider plus flutter_secure_storage. It is not connected to
 * MainActivity or application startup; [createLegacyHeadlessSnapshotReader]
 * is the future integration boundary after signed-install tests are green.
 */
class FlutterLegacyEnvelopeTransport(
    context: Context,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) : LegacyEnvelopeTransport {
    private val applicationContext = context.applicationContext
    private val requested = AtomicBoolean(false)
    private val terminal = AtomicBoolean(false)
    private val disposed = AtomicBoolean(false)

    @Volatile
    private var callback: ((LegacyEnvelopeTransportResult) -> Unit)? = null

    private var engine: FlutterEngine? = null
    private var channel: MethodChannel? = null
    private var readRequested = false

    override fun request(callback: (LegacyEnvelopeTransportResult) -> Unit) {
        if (!requested.compareAndSet(false, true)) {
            callback(LegacyEnvelopeTransportResult.Failed)
            return
        }
        this.callback = callback
        if (!mainHandler.post(::startOnMainThread)) {
            finish(LegacyEnvelopeTransportResult.Failed)
        }
    }

    private fun startOnMainThread() {
        if (terminal.get()) return
        try {
            val createdEngine = FlutterEngine(
                applicationContext,
                null,
                false,
            )
            engine = createdEngine
            createdEngine.plugins.add(PathProviderPlugin())
            createdEngine.plugins.add(FlutterSecureStoragePlugin())

            val createdChannel = MethodChannel(
                createdEngine.dartExecutor.binaryMessenger,
                LegacyMigrationChannelContract.channelName,
            )
            channel = createdChannel
            createdChannel.setMethodCallHandler(::handleDartCall)

            val bundlePath = FlutterInjector.instance()
                .flutterLoader()
                .findAppBundlePath()
            createdEngine.dartExecutor.executeDartEntrypoint(
                DartExecutor.DartEntrypoint(
                    bundlePath,
                    LegacyMigrationChannelContract.entrypointLibrary,
                    LegacyMigrationChannelContract.entrypointFunction,
                ),
            )
        } catch (_: RuntimeException) {
            finish(LegacyEnvelopeTransportResult.Failed)
        }
    }

    private fun handleDartCall(call: MethodCall, result: MethodChannel.Result) {
        val version = (call.arguments as? Number)?.toInt()
        if (call.method != LegacyMigrationChannelContract.readyMethod ||
            version != LegacyMigrationChannelContract.protocolVersion ||
            readRequested || terminal.get()
        ) {
            result.error(
                LegacyMigrationChannelContract.failureCode,
                "Legacy migration bridge protocol failed",
                null,
            )
            finish(LegacyEnvelopeTransportResult.Failed)
            return
        }

        readRequested = true
        result.success(null)
        val activeChannel = channel
        if (activeChannel == null) {
            finish(LegacyEnvelopeTransportResult.Failed)
            return
        }
        activeChannel.invokeMethod(
            LegacyMigrationChannelContract.readMethod,
            LegacyMigrationChannelContract.protocolVersion,
            object : MethodChannel.Result {
                override fun success(result: Any?) {
                    when (result) {
                        null -> finish(LegacyEnvelopeTransportResult.NotNeeded)
                        is ByteArray -> {
                            if (result.size > LegacySnapshotLimits.MAX_ENVELOPE_BYTES) {
                                finish(LegacyEnvelopeTransportResult.Failed)
                            } else {
                                finish(LegacyEnvelopeTransportResult.Available(result))
                            }
                        }
                        else -> finish(LegacyEnvelopeTransportResult.Failed)
                    }
                }

                override fun error(
                    errorCode: String,
                    errorMessage: String?,
                    errorDetails: Any?,
                ) {
                    finish(LegacyEnvelopeTransportResult.Failed)
                }

                override fun notImplemented() {
                    finish(LegacyEnvelopeTransportResult.Failed)
                }
            },
        )
    }

    private fun finish(reply: LegacyEnvelopeTransportResult) {
        if (!terminal.compareAndSet(false, true)) return
        disposeOnMainThread()
        callback?.invoke(reply)
        callback = null
    }

    override fun close() {
        terminal.set(true)
        callback = null
        if (Looper.myLooper() == mainHandler.looper) {
            disposeOnMainThread()
            return
        }

        val completed = CountDownLatch(1)
        if (!mainHandler.post {
                disposeOnMainThread()
                completed.countDown()
            }
        ) {
            return
        }
        try {
            completed.await(CLOSE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun disposeOnMainThread() {
        if (!disposed.compareAndSet(false, true)) return
        try {
            channel?.setMethodCallHandler(null)
        } catch (_: RuntimeException) {
            // Continue destruction even if channel detachment fails.
        }
        channel = null
        try {
            engine?.destroy()
        } catch (_: RuntimeException) {
            // Disposal is best-effort after terminal success/failure.
        }
        engine = null
    }

    private companion object {
        const val CLOSE_TIMEOUT_MILLIS = 5_000L
    }
}

fun createLegacyHeadlessSnapshotReader(
    context: Context,
    timeoutMillis: Long = 30_000L,
): LegacySnapshotReader = LegacyHeadlessSnapshotReader(
    transportFactory = LegacyEnvelopeTransportFactory {
        FlutterLegacyEnvelopeTransport(context)
    },
    timeoutMillis = timeoutMillis,
    isMainThread = { Looper.myLooper() == Looper.getMainLooper() },
)
