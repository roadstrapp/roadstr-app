package app.roadstr.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Compose equivalents of main's RoadstrGlassSurface and premium pressables. */
enum class RoadstrGlassLevel { Light, Medium, Strong }

/**
 * Same surface as main's RoadstrGlassSurface: a faint white sheen at the top
 * left, the plain fill through the middle and a whisper of the accent at the
 * bottom right. The sheen used to take 12% of the accent on dark themes, which
 * read as a bright smear across every panel.
 */
@Composable
fun roadstrGlassFill(level: RoadstrGlassLevel = RoadstrGlassLevel.Medium): Brush {
    val base = MaterialTheme.colorScheme.surface
    val accent = MaterialTheme.colorScheme.primary
    val alpha = when (level) {
        RoadstrGlassLevel.Light -> 0.72f
        RoadstrGlassLevel.Medium -> 0.88f
        RoadstrGlassLevel.Strong -> 0.97f
    }
    val fill = base.copy(alpha = alpha)
    return Brush.linearGradient(
        0f to lerp(fill, Color.White, 0.055f),
        0.54f to fill,
        1f to lerp(fill, accent, 0.055f),
    )
}

@Composable
fun RoadstrGlassSurface(
    modifier: Modifier = Modifier,
    level: RoadstrGlassLevel = RoadstrGlassLevel.Medium,
    shape: Shape = RoundedCornerShape(22.dp),
    padding: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val borderAlpha = when (level) {
        RoadstrGlassLevel.Light -> 0.26f
        RoadstrGlassLevel.Medium -> 0.34f
        RoadstrGlassLevel.Strong -> 0.46f
    }
    Surface(
        modifier = modifier
            .shadow(if (level == RoadstrGlassLevel.Strong) 10.dp else 6.dp, shape)
            .then(
                if (onClick == null) Modifier else Modifier
                    .clickable(role = Role.Button, onClick = onClick)
                    .semantics { role = Role.Button },
            ),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(0.9.dp, MaterialTheme.colorScheme.primary.copy(alpha = borderAlpha)),
    ) {
        Row(
            modifier = Modifier
                .background(roadstrGlassFill(level), shape)
                .padding(padding),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun RoadstrGlassBox(
    modifier: Modifier = Modifier,
    level: RoadstrGlassLevel = RoadstrGlassLevel.Medium,
    shape: Shape = RoundedCornerShape(22.dp),
    padding: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val borderAlpha = when (level) {
        RoadstrGlassLevel.Light -> 0.26f
        RoadstrGlassLevel.Medium -> 0.34f
        RoadstrGlassLevel.Strong -> 0.46f
    }
    Surface(
        modifier = modifier
            .shadow(if (level == RoadstrGlassLevel.Strong) 10.dp else 6.dp, shape)
            .then(
                if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick),
            ),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(0.9.dp, MaterialTheme.colorScheme.primary.copy(alpha = borderAlpha)),
    ) {
        Box(
            modifier = Modifier
                .background(roadstrGlassFill(level), shape)
                .padding(padding),
        ) { content() }
    }
}

@Composable
fun RoadstrAccentButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.96f),
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.88f),
                        ),
                    ),
                    RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun RoadstrIconButton(
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    RoadstrGlassSurface(
        modifier = modifier.size(48.dp),
        level = RoadstrGlassLevel.Medium,
        shape = CircleShape,
        padding = 0.dp,
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

/** Switch palette matching main: the inactive thumb/track stay visible on both themes. */
@Composable
fun RoadstrSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.onSurface.luminance() >
        MaterialTheme.colorScheme.surface.luminance()
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.78f),
            checkedBorderColor = MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = if (dark) Color(0xFFB8B8C5) else Color(0xFF8F8F99),
            uncheckedTrackColor = if (dark) Color(0xFF3A3A4A) else Color(0xFFE0E0E6),
            uncheckedBorderColor = if (dark) Color(0xFF6A6A7A) else Color(0xFF9B9BA5),
        ),
    )
}

@Composable
fun RoadstrSectionHeader(
    title: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { role = Role.Button }
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 18.dp, height = 4.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary),
                    ),
                    RoundedCornerShape(99.dp),
                ),
        )
        Text(
            text = title.uppercase(),
            modifier = Modifier.padding(horizontal = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = if (expanded) "⌃" else "⌄",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}
