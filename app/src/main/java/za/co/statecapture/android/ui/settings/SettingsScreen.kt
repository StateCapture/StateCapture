package za.co.statecapture.android.ui.settings

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu

import androidx.compose.ui.platform.LocalContext
import za.co.statecapture.android.data.repository.UserSettings
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onMenuClick: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    
    var showTimePicker by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleReminders(context, true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("Notifications", style = MaterialTheme.typography.titleMedium)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Monthly Purchase Reminder")
                    Text(
                        "Notify at the start of every month",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.remindersEnabled, 
                    onCheckedChange = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.toggleReminders(context, true)
                            }
                        } else {
                            viewModel.toggleReminders(context, enabled)
                        }
                    }
                )
            }
            
            if (settings.remindersEnabled) {
                Spacer(modifier = Modifier.height(24.dp))
                
                Text("Reminder Timing", style = MaterialTheme.typography.labelMedium)
                
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Day of Month")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { 
                                    if (settings.reminderDay > 1) {
                                        viewModel.updateTime(context, settings.reminderDay - 1, settings.reminderHour, settings.reminderMinute)
                                    }
                                }) {
                                    Text("-")
                                }
                                Text("${settings.reminderDay}", style = MaterialTheme.typography.titleMedium)
                                IconButton(onClick = { 
                                    if (settings.reminderDay < 28) { // Safe limit
                                        viewModel.updateTime(context, settings.reminderDay + 1, settings.reminderHour, settings.reminderMinute)
                                    }
                                }) {
                                    Text("+")
                                }
                            }
                        }
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Time")
                            TextButton(onClick = { showTimePicker = true }) {
                                Text(
                                    String.format("%02d:%02d", settings.reminderHour, settings.reminderMinute),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showTimePicker) {
            TimePickerDialog(
                initialHour = settings.reminderHour,
                initialMinute = settings.reminderMinute,
                onDismiss = { showTimePicker = false },
                onConfirm = { hour, minute ->
                    viewModel.updateTime(context, settings.reminderDay, hour, minute)
                    showTimePicker = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = { Text("Select Time") },
        text = {
            TimePicker(state = state)
        }
    )
}
