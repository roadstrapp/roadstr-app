package app.roadstr.feature.map

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.cos
import kotlin.math.roundToInt

enum class NativeMapCursorStyle {
    Arrow,
    Formula1,
    Suv,
    Racing,
    Electric,
    City,
    Classic500,
    Ostrich,
}

data class NativeMapCursorSnapshot(
    val sequence: Long,
    val point: NativeMapPoint?,
    val colorArgb: Long,
    val style: NativeMapCursorStyle = NativeMapCursorStyle.Arrow,
    val walkingMode: Boolean = false,
    val speedKilometresPerHour: Double = 0.0,
)

/** Value-only, revision-safe boundary for the future native location cursor. */
class NativeMapCursorSession(
    initialColorArgb: Long = NativeMapCursorVisualPolicy.DEFAULT_COLOR_ARGB,
    initialStyle: NativeMapCursorStyle = NativeMapCursorStyle.Arrow,
) {
    private val lock = Any()
    private val _state = MutableStateFlow(
        NativeMapCursorSnapshot(
            sequence = NO_CURSOR_SEQUENCE,
            point = null,
            colorArgb = validateColor(initialColorArgb),
            style = initialStyle,
        ),
    )

    val state: StateFlow<NativeMapCursorSnapshot> = _state.asStateFlow()

    fun submitPosition(sequence: Long, point: NativeMapPoint): Boolean = synchronized(lock) {
        require(sequence >= 0) { "Cursor sequence must be non-negative" }
        requireValidPoint(point)
        if (sequence <= _state.value.sequence) return false
        _state.value = _state.value.copy(sequence = sequence, point = point)
        true
    }

    /** Hides the cursor and fences late fixes at or below [sequence]. */
    fun clear(sequence: Long): Boolean = synchronized(lock) {
        require(sequence >= 0) { "Cursor sequence must be non-negative" }
        if (sequence <= _state.value.sequence) return false
        _state.value = _state.value.copy(sequence = sequence, point = null)
        true
    }

    fun updateColor(colorArgb: Long): Boolean = synchronized(lock) {
        val validated = validateColor(colorArgb)
        if (_state.value.colorArgb == validated) return false
        _state.value = _state.value.copy(colorArgb = validated)
        true
    }

    fun updateStyle(style: NativeMapCursorStyle): Boolean = synchronized(lock) {
        if (_state.value.style == style) return false
        _state.value = _state.value.copy(style = style)
        true
    }

    /** Walking temporarily overrides the saved vehicle without destroying it. */
    fun updateMotion(walkingMode: Boolean, speedMetersPerSecond: Double): Boolean = synchronized(lock) {
        require(speedMetersPerSecond.isFinite() && speedMetersPerSecond >= 0.0)
        val speedKmh = (speedMetersPerSecond * 3.6).coerceAtMost(40.0)
        if (
            _state.value.walkingMode == walkingMode &&
            kotlin.math.abs(_state.value.speedKilometresPerHour - speedKmh) < 0.05
        ) {
            return false
        }
        _state.value = _state.value.copy(
            walkingMode = walkingMode,
            speedKilometresPerHour = speedKmh,
        )
        true
    }

    private companion object {
        const val NO_CURSOR_SEQUENCE = -1L

        fun requireValidPoint(point: NativeMapPoint) {
            require(point.latitude.isFinite() && point.latitude in -90.0..90.0) {
                "Cursor latitude is outside the WGS84 range"
            }
            require(point.longitude.isFinite() && point.longitude in -180.0..180.0) {
                "Cursor longitude is outside the WGS84 range"
            }
        }

        fun validateColor(value: Long): Long {
            require(value in 0L..0xFFFF_FFFFL) { "Cursor color must be a 32-bit ARGB value" }
            return value
        }
    }
}

data class NativeMapCursorVisualFrame(
    val pitchFraction: Double,
    val flatYScale: Double,
    val shadowCenterY: Double,
    val shadowScaleX: Double,
    val shadowScaleY: Double,
    val shadowAlpha: Double,
)

/** Pure geometry and color policy copied from Flutter's MapLibre cursor painter. */
object NativeMapCursorVisualPolicy {
    const val WIDTH_DP = 48.0
    const val HEIGHT_DP = 76.0
    const val DISPLAY_SCALE = 1.4
    const val MAX_PITCH_DEGREES = 60.0
    const val DEFAULT_COLOR_ARGB = 0xFF8B3DFF

    fun frame(pitchDegrees: Double): NativeMapCursorVisualFrame {
        require(pitchDegrees.isFinite()) { "Cursor pitch must be finite" }
        val pitch = pitchDegrees.coerceIn(0.0, MAX_PITCH_DEGREES)
        val fraction = pitch / MAX_PITCH_DEGREES
        return NativeMapCursorVisualFrame(
            pitchFraction = fraction,
            flatYScale = cos(Math.toRadians(pitch)).coerceAtLeast(0.5),
            shadowCenterY = 36.0 + fraction * 14.0,
            shadowScaleX = 1.0 - fraction * 0.1,
            shadowScaleY = 0.5 + fraction * 0.3,
            shadowAlpha = 0.35 - fraction * 0.05,
        )
    }

    /** HSL lightness adjustment matching Flutter's gradient/stroke treatment. */
    fun adjustLightness(argb: Long, delta: Double): Int {
        require(argb in 0L..0xFFFF_FFFFL) { "Cursor color must be a 32-bit ARGB value" }
        require(delta.isFinite()) { "Cursor lightness delta must be finite" }
        val value = argb.toInt()
        val alpha = value ushr 24 and 0xFF
        val red = (value ushr 16 and 0xFF) / 255.0
        val green = (value ushr 8 and 0xFF) / 255.0
        val blue = (value and 0xFF) / 255.0
        val maximum = maxOf(red, green, blue)
        val minimum = minOf(red, green, blue)
        val lightness = (maximum + minimum) / 2.0
        val chroma = maximum - minimum
        val saturation = if (chroma == 0.0) {
            0.0
        } else {
            chroma / (1.0 - kotlin.math.abs(2.0 * lightness - 1.0))
        }
        val hue = when {
            chroma == 0.0 -> 0.0
            maximum == red -> 60.0 * (((green - blue) / chroma) % 6.0)
            maximum == green -> 60.0 * ((blue - red) / chroma + 2.0)
            else -> 60.0 * ((red - green) / chroma + 4.0)
        }.let { if (it < 0.0) it + 360.0 else it }
        return hslToArgb(alpha, hue, saturation, (lightness + delta).coerceIn(0.0, 1.0))
    }

    private fun hslToArgb(alpha: Int, hue: Double, saturation: Double, lightness: Double): Int {
        val chroma = (1.0 - kotlin.math.abs(2.0 * lightness - 1.0)) * saturation
        val section = hue / 60.0
        val secondary = chroma * (1.0 - kotlin.math.abs(section % 2.0 - 1.0))
        val (red, green, blue) = when (section.toInt()) {
            0 -> Triple(chroma, secondary, 0.0)
            1 -> Triple(secondary, chroma, 0.0)
            2 -> Triple(0.0, chroma, secondary)
            3 -> Triple(0.0, secondary, chroma)
            4 -> Triple(secondary, 0.0, chroma)
            else -> Triple(chroma, 0.0, secondary)
        }
        val offset = lightness - chroma / 2.0
        fun channel(value: Double): Int = ((value + offset) * 255.0).roundToInt().coerceIn(0, 255)
        return (alpha shl 24) or
            (channel(red) shl 16) or
            (channel(green) shl 8) or
            channel(blue)
    }
}
