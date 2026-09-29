package app.roadstr.service.network

import app.roadstr.core.network.BoundedHttpBodyBudget
import app.roadstr.core.network.BoundedHttpPolicy
import app.roadstr.core.network.NetworkResponseLimit
import app.roadstr.core.network.NetworkTimeoutBudget
import app.roadstr.core.network.RoutingProviderRequest
import app.roadstr.core.network.RoutingRequestHttpMethod
import app.roadstr.core.network.SearchProviderHttpMethod
import app.roadstr.core.network.SearchProviderRequest
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class NativeHttpMethod {
    Get,
    Post,
}

data class NativeHttpRequest(
    val method: NativeHttpMethod,
    val uri: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
)

data class NativeHttpResponse(
    val statusCode: Int,
    val reasonPhrase: String,
    val headers: Map<String, String>,
    val bodyBytes: ByteArray,
) {
    val bodyUtf8: String
        get() = bodyBytes.toString(Charsets.UTF_8)
}

data class NativeHttpRequestLimits(
    val timeoutMillis: Long,
    val maxResponseBytes: Long,
) {
    init {
        require(timeoutMillis > 0) { "HTTP timeout must be positive" }
        require(maxResponseBytes in 1..Int.MAX_VALUE.toLong()) {
            "HTTP response budget must fit a byte array"
        }
    }
}

/** Narrow transport boundary used by the native routing service. */
fun interface NativeRoutingHttpTransport {
    suspend fun execute(
        request: RoutingProviderRequest,
        limits: NativeHttpRequestLimits,
    ): NativeHttpResponse
}

/** Narrow transport boundary used by the native place-search service. */
fun interface NativeSearchHttpTransport {
    suspend fun execute(
        request: SearchProviderRequest,
        limits: NativeHttpRequestLimits,
    ): NativeHttpResponse
}

/** Narrow transport boundary used by the native public-transport service. */
fun interface NativeTransitHttpTransport {
    suspend fun execute(
        request: NativeHttpRequest,
        limits: NativeHttpRequestLimits,
    ): NativeHttpResponse
}

enum class NativeHttpFailureKind {
    InvalidRequest,
    ResponseTooLarge,
    Timeout,
    Transport,
}

/**
 * Value-free native transport failure.
 *
 * Messages deliberately omit the request URI, headers and body because they
 * can contain precise coordinates, provider API keys or Overpass queries.
 */
class NativeHttpException(
    val kind: NativeHttpFailureKind,
) : IOException(
    when (kind) {
        NativeHttpFailureKind.InvalidRequest -> "Invalid HTTP request"
        NativeHttpFailureKind.ResponseTooLarge -> "HTTP response is too large"
        NativeHttpFailureKind.Timeout -> "HTTP request timed out"
        NativeHttpFailureKind.Transport -> "HTTP transport failed"
    },
)

/**
 * Cancellable OkHttp adapter backed by the fixture-locked Roadstr policies.
 *
 * One client instance should be shared by native services so its dispatcher
 * and connection pool are reused. Each call receives an explicit total
 * deadline and response budget. The adapter is not wired to app startup yet.
 */
class NativeBoundedHttpClient private constructor(
    private val callFactory: Call.Factory,
) : NativeRoutingHttpTransport, NativeSearchHttpTransport, NativeTransitHttpTransport {
    constructor() : this(defaultClient())

    suspend fun execute(
        request: NativeHttpRequest,
        timeout: NetworkTimeoutBudget,
        responseLimit: NetworkResponseLimit,
    ): NativeHttpResponse = execute(
        request = request,
        limits = NativeHttpRequestLimits(
            timeoutMillis = timeout.milliseconds,
            maxResponseBytes = responseLimit.bytes,
        ),
    )

    override suspend fun execute(
        request: NativeHttpRequest,
        limits: NativeHttpRequestLimits,
    ): NativeHttpResponse = executeWithBounds(
        request = request,
        timeoutMillis = limits.timeoutMillis,
        maxBytes = limits.maxResponseBytes,
    )

    suspend fun execute(
        request: RoutingProviderRequest,
        timeout: NetworkTimeoutBudget,
        responseLimit: NetworkResponseLimit,
    ): NativeHttpResponse = execute(
        request = request.toNativeRequest(),
        timeout = timeout,
        responseLimit = responseLimit,
    )

    override suspend fun execute(
        request: RoutingProviderRequest,
        limits: NativeHttpRequestLimits,
    ): NativeHttpResponse = execute(
        request = request.toNativeRequest(),
        limits = limits,
    )

    override suspend fun execute(
        request: SearchProviderRequest,
        limits: NativeHttpRequestLimits,
    ): NativeHttpResponse = execute(
        request = request.toNativeRequest(),
        limits = limits,
    )

    suspend fun execute(
        request: SearchProviderRequest,
        timeout: NetworkTimeoutBudget,
        responseLimit: NetworkResponseLimit,
    ): NativeHttpResponse = execute(
        request = request,
        limits = NativeHttpRequestLimits(
            timeoutMillis = timeout.milliseconds,
            maxResponseBytes = responseLimit.bytes,
        ),
    )

    internal suspend fun executeWithBounds(
        request: NativeHttpRequest,
        timeoutMillis: Long,
        maxBytes: Long,
    ): NativeHttpResponse {
        require(timeoutMillis > 0) { "HTTP timeout must be positive" }
        require(maxBytes in 1..Int.MAX_VALUE.toLong()) {
            "HTTP response budget must fit a byte array"
        }

        val nativeRequest = request.toOkHttpRequest()
        return suspendCancellableCoroutine { continuation ->
            val call = try {
                callFactory.newCall(nativeRequest)
            } catch (_: RuntimeException) {
                continuation.resumeWithException(
                    NativeHttpException(NativeHttpFailureKind.InvalidRequest),
                )
                return@suspendCancellableCoroutine
            }
            call.timeout().timeout(timeoutMillis, TimeUnit.MILLISECONDS)
            continuation.invokeOnCancellation { call.cancel() }
            if (!continuation.isActive) {
                call.cancel()
                return@suspendCancellableCoroutine
            }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isCancelled) return
                        val kind = if (e is InterruptedIOException) {
                            NativeHttpFailureKind.Timeout
                        } else {
                            NativeHttpFailureKind.Transport
                        }
                        continuation.resumeWithException(NativeHttpException(kind))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            response.use {
                                if (continuation.isActive) {
                                    continuation.resume(response.toBoundedResponse(maxBytes))
                                }
                            }
                        } catch (exception: NativeHttpException) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(exception)
                            }
                        } catch (exception: InterruptedIOException) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    NativeHttpException(NativeHttpFailureKind.Timeout),
                                )
                            }
                        } catch (_: IOException) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    NativeHttpException(NativeHttpFailureKind.Transport),
                                )
                            }
                        }
                    }
                },
            )
        }
    }

    private fun NativeHttpRequest.toOkHttpRequest(): Request {
        try {
            val builder = Request.Builder().url(uri)
            for ((name, value) in headers) {
                builder.header(name, value)
            }
            when (method) {
                NativeHttpMethod.Get -> builder.get()
                NativeHttpMethod.Post -> builder.post((body ?: "").toRequestBody())
            }
            return builder.build()
        } catch (_: RuntimeException) {
            throw NativeHttpException(NativeHttpFailureKind.InvalidRequest)
        }
    }

    private fun Response.toBoundedResponse(maxBytes: Long): NativeHttpResponse {
        val responseBody = body
        val declaredLength = responseBody?.contentLength()?.takeIf { it >= 0 }
        if (!BoundedHttpPolicy.acceptsContentLength(declaredLength, maxBytes)) {
            throw NativeHttpException(NativeHttpFailureKind.ResponseTooLarge)
        }

        val budget = BoundedHttpBodyBudget(maxBytes)
        val initialCapacity = when {
            responseBody == null -> 0
            declaredLength != null -> declaredLength.coerceAtMost(maxBytes).toInt()
            else -> minOf(DEFAULT_INITIAL_CAPACITY, maxBytes.toInt())
        }
        val output = ByteArrayOutputStream(initialCapacity)
        if (responseBody != null) {
            val input = responseBody.byteStream()
            val chunk = ByteArray(STREAM_CHUNK_BYTES)
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                if (read == 0) continue
                if (!budget.acceptChunk(read)) {
                    throw NativeHttpException(NativeHttpFailureKind.ResponseTooLarge)
                }
                output.write(chunk, 0, read)
            }
        }

        val normalizedHeaders = linkedMapOf<String, String>()
        for (name in headers.names()) {
            normalizedHeaders[name.lowercase(Locale.ROOT)] =
                headers.values(name).joinToString(",")
        }
        return NativeHttpResponse(
            statusCode = code,
            reasonPhrase = message,
            headers = normalizedHeaders,
            bodyBytes = output.toByteArray(),
        )
    }

    private fun RoutingProviderRequest.toNativeRequest(): NativeHttpRequest =
        NativeHttpRequest(
            method = when (method) {
                RoutingRequestHttpMethod.Get -> NativeHttpMethod.Get
                RoutingRequestHttpMethod.Post -> NativeHttpMethod.Post
            },
            uri = uri,
            headers = headers,
            body = body,
        )

    private fun SearchProviderRequest.toNativeRequest(): NativeHttpRequest =
        NativeHttpRequest(
            method = when (method) {
                SearchProviderHttpMethod.Get -> NativeHttpMethod.Get
                SearchProviderHttpMethod.Post -> NativeHttpMethod.Post
            },
            uri = uri,
            headers = headers,
            body = body,
        )

    companion object {
        private const val STREAM_CHUNK_BYTES = 8 * 1024
        private const val DEFAULT_INITIAL_CAPACITY = 8 * 1024

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(BoundedHttpPolicy.followRedirects)
            .followSslRedirects(BoundedHttpPolicy.followRedirects)
            .retryOnConnectionFailure(false)
            // The per-call total timeout below is the only deadline. Leaving
            // phase-specific defaults enabled would truncate the 12–25 second
            // Roadstr budgets at OkHttp's shorter defaults.
            .connectTimeout(0, TimeUnit.MILLISECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        internal fun forTesting(callFactory: Call.Factory): NativeBoundedHttpClient =
            NativeBoundedHttpClient(callFactory)
    }
}
