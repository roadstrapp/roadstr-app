package app.roadstr.feature.home

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.roadstr.R
import app.roadstr.core.ui.theme.RoadstrTheme
import app.roadstr.core.ui.theme.RoadstrThemeId
import app.roadstr.feature.map.NativeMapLibreHost
import app.roadstr.feature.map.NativeRouteOverlaySession

/**
 * Dormant native UI boundary used to prove Compose and MapLibre packaging.
 *
 * It owns no storage, location or migration state and is not the launcher. If
 * invoked explicitly from inside the app, only the admitted OSM raster source
 * may perform network I/O. Product screens replace this boundary incrementally
 * after their parity gates are green.
 */
@Composable
fun NativeRoadstrShell() {
    val themeId = if (isSystemInDarkTheme()) {
        RoadstrThemeId.DarkNostr
    } else {
        RoadstrThemeId.LightNostr
    }
    RoadstrTheme(themeId = themeId) {
        val shellDescription = stringResource(R.string.native_shell_description)
        val routeSession = remember { NativeRouteOverlaySession(themeId.accentArgb) }
        LaunchedEffect(routeSession, themeId.accentArgb) {
            routeSession.updateAccent(themeId.accentArgb)
        }
        val routeState by routeSession.state.collectAsState()
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = shellDescription },
            containerColor = MaterialTheme.colorScheme.background,
        ) { contentPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                NativeMapLibreHost(
                    dark = themeId.dark,
                    routeOverlay = routeState.snapshot,
                    modifier = Modifier.fillMaxSize(),
                )
                Surface(
                    modifier = Modifier
                        .padding(16.dp)
                        .wrapContentSize(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 4.dp,
                ) {
                    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.native_map_canary_status),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}
