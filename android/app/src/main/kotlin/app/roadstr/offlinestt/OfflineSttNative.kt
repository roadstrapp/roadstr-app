package app.roadstr.offlinestt

/**
 * The raw JNI surface. Do not call this directly: [OfflineSttEngine] owns the lifecycle.
 * Failures are thrown as plain JDK exceptions so code shrinking cannot break the native side.
 */
internal object OfflineSttNative {
    /** Returns an opaque engine id. Throws [java.io.IOException] if the model is missing or rejected. */
    @JvmStatic
    external fun load(modelPath: String, language: String?, threads: Int): Long

    /** Returns `[text, detectedLanguage or "", audioMs, decodeMs]`. */
    @JvmStatic
    external fun transcribe(id: Long, pcm: FloatArray, length: Int): Array<String>

    @JvmStatic
    external fun cancel(id: Long)

    @JvmStatic
    external fun release(id: Long)

    @JvmStatic
    external fun version(): String
}
