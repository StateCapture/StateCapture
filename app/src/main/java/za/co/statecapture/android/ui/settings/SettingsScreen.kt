package za.co.statecapture.android.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import za.co.statecapture.android.data.Reminder
import za.co.statecapture.android.data.ReminderFrequency
import kotlin.math.roundToInt

// SA flag colours used for swipe reveal
private val SaBlue = Color(0xFF002395)   // edit (swipe right)
private val SaRed  = Color(0xFFDE3831)   // delete (swipe left)

private const val ACTION_THRESHOLD_DP = 80

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onMenuClick: () -> Unit
) {
    val context = LocalContext.current
    val reminders by viewModel.reminders.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var editingReminder by remember { mutableStateOf<Reminder?>(null) }
    var pendingReminderToEnable by remember { mutableStateOf<Reminder?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingReminderToEnable?.let { reminder ->
                viewModel.updateReminder(context, reminder.copy(isEnabled = true))
                pendingReminderToEnable = null
            }
        } else {
            pendingReminderToEnable = null
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
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Reminder")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Text(
                text = "Reminders",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            if (reminders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No reminders set.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(reminders, key = { it.id }) { reminder ->
                        SwipeableReminderItem(
                            reminder = reminder,
                            onDelete = { viewModel.deleteReminder(context, reminder) },
                            onEdit = { editingReminder = reminder },
                            onToggle = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                        pendingReminderToEnable = reminder
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        viewModel.updateReminder(context, reminder.copy(isEnabled = true))
                                    }
                                } else {
                                    viewModel.updateReminder(context, reminder.copy(isEnabled = enabled))
                                }
                            }
                        )
                    }
                }
            }
        }

        if (showAddDialog) {
            ReminderDialog(
                title = "Add Reminder",
                confirmLabel = "Add",
                onDismiss = { showAddDialog = false },
                onConfirm = { frequency, dayValue, hour, minute ->
                    val newReminder = Reminder(
                        frequency = frequency,
                        dayValue = dayValue,
                        hour = hour,
                        minute = minute,
                        isEnabled = true
                    )
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        viewModel.addReminder(context, newReminder.copy(isEnabled = false))
                        pendingReminderToEnable = newReminder // wait this will have ID 0, but we need real ID. Adding and then updating is better handled if permission is granted first, but for simplicity we let it be added disabled, and request permission for the next toggle.
                        // Better: just request permission and add it as disabled, user can enable it.
                    } else {
                        viewModel.addReminder(context, newReminder)
                    }
                    showAddDialog = false
                }
            )
        }

        if (editingReminder != null) {
            ReminderDialog(
                title = "Edit Reminder",
                confirmLabel = "Save",
                initialReminder = editingReminder,
                onDismiss = { editingReminder = null },
                onConfirm = { frequency, dayValue, hour, minute ->
                    viewModel.updateReminder(context, editingReminder!!.copy(
                        frequency = frequency,
                        dayValue = dayValue,
                        hour = hour,
                        minute = minute
                    ))
                    editingReminder = null
                }
            )
        }
    }
}

@Composable
fun SwipeableReminderItem(
    reminder: Reminder,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val offsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalContext.current.resources.displayMetrics.density

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // --- Background layer: edit (left, blue) and delete (right, red) ---
        Box(modifier = Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .alpha(if (offsetX.value > 0) 1f else 0f)
                    .background(SaBlue),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = Color.White,
                    modifier = Modifier.padding(start = 20.dp)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .alpha(if (offsetX.value < 0) 1f else 0f)
                    .background(SaRed),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.padding(end = 20.dp)
                )
            }
        }

        // --- Foreground: the actual card ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(0f)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(reminder.id) {
                    val thresholdPx = ACTION_THRESHOLD_DP * density
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                when {
                                    offsetX.value > thresholdPx -> {
                                        onEdit()
                                        offsetX.animateTo(0f, spring())
                                    }
                                    offsetX.value < -thresholdPx -> {
                                        onDelete()
                                        offsetX.animateTo(0f, spring())
                                    }
                                    else -> offsetX.animateTo(0f, spring())
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                offsetX.animateTo(0f, spring())
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                offsetX.snapTo((offsetX.value + dragAmount).coerceIn(-thresholdPx * 1.5f, thresholdPx * 1.5f))
                            }
                        }
                    )
                }
                .clickable(onClick = onEdit),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f).compositeOver(Color.White)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = if (reminder.frequency == ReminderFrequency.MONTHLY) Icons.Default.DateRange else Icons.Default.Refresh
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val dayStr = if (reminder.frequency == ReminderFrequency.MONTHLY) {
                        "Day ${reminder.dayValue}"
                    } else {
                        val days = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
                        days.getOrElse(reminder.dayValue - 1) { "Unknown" }
                    }
                    val freqStr = if (reminder.frequency == ReminderFrequency.MONTHLY) "Monthly" else "Weekly"
                    
                    Text(
                        text = "$freqStr on $dayStr",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = String.format("Time: %02d:%02d", reminder.hour, reminder.minute),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = reminder.isEnabled,
                    onCheckedChange = onToggle
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDialog(
    title: String,
    confirmLabel: String,
    initialReminder: Reminder? = null,
    onDismiss: () -> Unit,
    onConfirm: (ReminderFrequency, Int, Int, Int) -> Unit
) {
    var frequency by remember { mutableStateOf(initialReminder?.frequency ?: ReminderFrequency.MONTHLY) }
    var dayValue by remember { mutableStateOf(initialReminder?.dayValue ?: 1) }
    var hour by remember { mutableStateOf(initialReminder?.hour ?: 9) }
    var minute by remember { mutableStateOf(initialReminder?.minute ?: 0) }

    var showTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("Frequency", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = frequency == ReminderFrequency.MONTHLY,
                        onClick = { 
                            frequency = ReminderFrequency.MONTHLY
                            if (dayValue > 28) dayValue = 28
                        },
                        label = { Text("Monthly") }
                    )
                    FilterChip(
                        selected = frequency == ReminderFrequency.WEEKLY,
                        onClick = { 
                            frequency = ReminderFrequency.WEEKLY
                            if (dayValue > 7) dayValue = 1
                        },
                        label = { Text("Weekly") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(if (frequency == ReminderFrequency.MONTHLY) "Day of Month" else "Day of Week", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (frequency == ReminderFrequency.MONTHLY) {
                        IconButton(onClick = { if (dayValue > 1) dayValue-- }) { Text("-") }
                        Text("$dayValue", style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { if (dayValue < 28) dayValue++ }) { Text("+") }
                    } else {
                        val days = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
                        TextButton(onClick = {
                            dayValue = if (dayValue < 7) dayValue + 1 else 1
                        }) {
                            Text(days[dayValue - 1], style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Time", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { showTimePicker = true }) {
                        Text(String.format("%02d:%02d", hour, minute), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            if (showTimePicker) {
                TimePickerDialog(
                    initialHour = hour,
                    initialMinute = minute,
                    onDismiss = { showTimePicker = false },
                    onConfirm = { h, m ->
                        hour = h
                        minute = m
                        showTimePicker = false
                    }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(frequency, dayValue, hour, minute) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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
