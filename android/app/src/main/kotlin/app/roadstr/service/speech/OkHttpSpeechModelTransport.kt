package app.roadstr.service.speech

import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Downloads the speech model over HTTPS with `Range` resuming. No overall deadline (the file is half
 * a gigabyte and the link may be slow), but a stalled connection times out after 30 s. A redirect
 * from HTTPS to plain HTTP is refused.
 */
class OkHttpSpeechModelTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .followSslRedirects(false)
        .build(),
) : SpeechModelTransport {
    override fun open(url: String, resumeFrom: Long): SpeechModelBody {
        val request = Request.Builder().url(url).get().apply {
            if (resumeFrom > 0) header("Range", "bytes=$resumeFrom-")
        }.build()
        val call = client.newCall(request)
        val response = try {
            call.execute()
        } catch (error: IOException) {
            throw SpeechModelException.Network("could not reach the model server", error)
        }
        return when (response.code) {
            200 -> body(call, response, startOffset = 0L)
            206 -> body(call, response, startOffset = resumedOffset(response, resumeFrom))
            416 -> rejectRange(response)
            else -> fail(response)
        }
    }

    private fun resumedOffset(response: Response, requested: Long): Long {
        val start = response.header("Content-Range")
            ?.removePrefix("bytes ")
            ?.substringBefore('-')
            ?.toLongOrNull()
        if (start == requested) return requested
        response.close()
        throw SpeechModelException.RangeRejected()
    }

    private fun rejectRange(response: Response): Nothing {
        response.close()
        throw SpeechModelException.RangeRejected()
    }

    private fun fail(response: Response): Nothing {
        val code = response.code
        response.close()
        throw SpeechModelException.Network("the model server answered HTTP $code")
    }

    private fun body(call: Call, response: Response, startOffset: Long): SpeechModelBody {
        val source = response.body ?: run {
            response.close()
            throw SpeechModelException.Network("the model server sent no body")
        }
        return object : SpeechModelBody {
            override val startOffset: Long = startOffset
            override val stream: InputStream = source.byteStream()
            override fun close() {
                response.close()
                call.cancel()
            }
        }
    }
}
