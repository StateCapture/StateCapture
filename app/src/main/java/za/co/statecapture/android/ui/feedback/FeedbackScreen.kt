package za.co.statecapture.android.ui.feedback

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import za.co.statecapture.android.util.AppConstants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    onMenuClick: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Feedback & Support") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Help us improve!", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Don't see your provider?", style = MaterialTheme.typography.titleMedium)
            Text("We are constantly adding new providers. If yours is missing, please let us know using our dedicated form.", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { 
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AppConstants.SUGGEST_PROVIDER_FORM_URL))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Suggest a New Provider")
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            Text("Incorrect Tariffs?", style = MaterialTheme.typography.titleMedium)
            Text("If you notice that any tariff data is incorrect or outdated, please let us know so we can fix it.", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { 
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AppConstants.REPORT_TARIFF_FORM_URL))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Report Incorrect Tariffs")
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            Text("Other Bugs & Feedback", style = MaterialTheme.typography.titleMedium)
            Text("For any other bugs, or to show your support, please rate and review the app on the Google Play Store.", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { 
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AppConstants.PLAY_STORE_URL))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Rate on Play Store")
            }
        }
    }
}
