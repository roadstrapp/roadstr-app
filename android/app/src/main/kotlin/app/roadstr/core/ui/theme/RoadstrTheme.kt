package app.roadstr.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Stable theme identifiers matching the persisted Flutter ordinal contract. */
enum class RoadstrThemeId(
    val storedOrdinal: Int,
    val dark: Boolean,
    val accentArgb: Long,
) {
    LightNostr(0, false, RoadstrThemeTokens.NOSTR_PURPLE_ARGB),
    LightBitcoin(1, false, RoadstrThemeTokens.BITCOIN_ORANGE_ARGB),
    DarkNostr(2, true, RoadstrThemeTokens.NOSTR_PURPLE_ARGB),
    DarkBitcoin(3, true, RoadstrThemeTokens.BITCOIN_ORANGE_ARGB),
    ;

    companion object {
        /** Legacy ordinals 4-7 were aliases and must retain their old meaning. */
        fun fromStoredOrdinal(value: Int): RoadstrThemeId =
            when (value) {
                0, 4 -> LightNostr
                1, 5 -> LightBitcoin
                2, 6 -> DarkNostr
                3, 7 -> DarkBitcoin
                else -> LightNostr
            }
    }
}

/** Value-only color contract shared by Compose and host-side parity tests. */
data class RoadstrThemePalette(
    val accentArgb: Long,
    val backgroundArgb: Long,
    val surfaceArgb: Long,
    val borderArgb: Long,
    val textPrimaryArgb: Long,
    val textSecondaryArgb: Long,
)

object RoadstrThemeTokens {
    const val NOSTR_PURPLE_ARGB = 0xFF8B5CF6
    const val BITCOIN_ORANGE_ARGB = 0xFFF7931A

    const val LIGHT_BACKGROUND_ARGB = 0xFFF5F5F5
    const val LIGHT_SURFACE_ARGB = 0xFFFFFFFF
    const val LIGHT_BORDER_ARGB = 0xFFE0E0E0
    const val LIGHT_TEXT_PRIMARY_ARGB = 0xFF1A1A2E
    const val LIGHT_TEXT_SECONDARY_ARGB = 0xFF757575

    const val DARK_BACKGROUND_ARGB = 0xFF0D0D1A
    const val DARK_SURFACE_ARGB = 0xFF1A1A2E
    const val DARK_BORDER_ARGB = 0xFF2A2A40
    const val DARK_TEXT_PRIMARY_ARGB = 0xFFEEEEF8
    const val DARK_TEXT_SECONDARY_ARGB = 0xFF8888A8

    /** Main's raised tone (RoadstrColors.surface3); `surfaceVariant` in Compose. */
    const val LIGHT_SURFACE3_ARGB = 0xFFF0F0F0
    const val DARK_SURFACE3_ARGB = 0xFF22223A

    fun palette(themeId: RoadstrThemeId): RoadstrThemePalette =
        if (themeId.dark) {
            RoadstrThemePalette(
                accentArgb = themeId.accentArgb,
                backgroundArgb = DARK_BACKGROUND_ARGB,
                surfaceArgb = DARK_SURFACE_ARGB,
                borderArgb = DARK_BORDER_ARGB,
                textPrimaryArgb = DARK_TEXT_PRIMARY_ARGB,
                textSecondaryArgb = DARK_TEXT_SECONDARY_ARGB,
            )
        } else {
            RoadstrThemePalette(
                accentArgb = themeId.accentArgb,
                backgroundArgb = LIGHT_BACKGROUND_ARGB,
                surfaceArgb = LIGHT_SURFACE_ARGB,
                borderArgb = LIGHT_BORDER_ARGB,
                textPrimaryArgb = LIGHT_TEXT_PRIMARY_ARGB,
                textSecondaryArgb = LIGHT_TEXT_SECONDARY_ARGB,
            )
        }
}

fun roadstrColorScheme(themeId: RoadstrThemeId): ColorScheme {
    val palette = RoadstrThemeTokens.palette(themeId)
    val accent = Color(palette.accentArgb)
    return if (themeId.dark) {
        darkColorScheme(
            primary = accent,
            secondary = accent,
            background = Color(palette.backgroundArgb),
            surface = Color(palette.surfaceArgb),
            onSurface = Color(palette.textPrimaryArgb),
            outline = Color(palette.borderArgb),
            // Without these Material falls back to its own baseline greys and
            // purples, which are not Roadstr's.
            surfaceVariant = Color(RoadstrThemeTokens.DARK_SURFACE3_ARGB),
            onSurfaceVariant = Color(palette.textSecondaryArgb),
            outlineVariant = Color(palette.borderArgb),
            primaryContainer = accent.copy(alpha = 0.20f),
            onPrimaryContainer = accent,
        )
    } else {
        lightColorScheme(
            primary = accent,
            secondary = accent,
            background = Color(palette.backgroundArgb),
            surface = Color(palette.surfaceArgb),
            onSurface = Color(palette.textPrimaryArgb),
            outline = Color(palette.borderArgb),
            surfaceVariant = Color(RoadstrThemeTokens.LIGHT_SURFACE3_ARGB),
            onSurfaceVariant = Color(palette.textSecondaryArgb),
            outlineVariant = Color(palette.borderArgb),
            primaryContainer = accent.copy(alpha = 0.12f),
            onPrimaryContainer = accent,
        )
    }
}

@Composable
fun RoadstrTheme(
    themeId: RoadstrThemeId,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = roadstrColorScheme(themeId),
        content = content,
    )
}
