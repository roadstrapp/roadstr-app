package app.roadstr.feature.home

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.roadstr.R
import app.roadstr.core.ui.theme.RoadstrTheme
import app.roadstr.core.ui.theme.RoadstrThemeId

/**
 * Dormant native UI boundary used to prove Compose packaging and theme parity.
 *
 * It owns no storage, network, location or migration state and is not the
 * launcher. Product screens replace this boundary incrementally after their
 * parity gates are green.
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
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = shellDescription },
            containerColor = MaterialTheme.colorScheme.background,
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        text = stringResource(R.string.native_shell_status),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
