package za.co.statecapture.android.ui.about

import za.co.statecapture.android.BuildConfig
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import za.co.statecapture.android.util.AppConstants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onMenuClick: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            // Tapping the tagline picks a new random one (different from the current)
            var tagline by remember { mutableStateOf(AppConstants.TAGLINES.random()) }

            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = "The Prepaid",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "State Capture",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "App",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    letterSpacing = 2.sp
                )
            }
            Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = tagline,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    // Pick a different tagline each tap
                    tagline = (AppConstants.TAGLINES - tagline).random()
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Disclaimer:", style = MaterialTheme.typography.titleMedium)
            Text(
                AppConstants.APP_DISCLAIMER,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
