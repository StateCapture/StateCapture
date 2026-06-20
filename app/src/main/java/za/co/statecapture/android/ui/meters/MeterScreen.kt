package za.co.statecapture.android.ui.meters

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import za.co.statecapture.android.ui.components.SearchableProviderDialog
import za.co.statecapture.android.data.Meter
import za.co.statecapture.android.domain.model.TariffIndexItem
import za.co.statecapture.android.ui.theme.ProviderThemedBlock
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import za.co.statecapture.android.util.rememberReorderState
import za.co.statecapture.android.util.reorderable
import kotlin.math.roundToInt

// SA flag colours used for swipe reveal
private val SaBlue = Color(0xFF002395)   // edit (swipe right)
private val SaRed  = Color(0xFFDE3831)   // delete (swipe left)

// How far (px) the card can slide before it's considered an action trigger
private const val ACTION_THRESHOLD_DP = 80

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeterScreen(
    meters: List<Meter>,
    availableProviders: List<TariffIndexItem>,
    onAddMeter: (String, String, String, Boolean, String) -> Unit,
    onUpdateMeter: (Meter) -> Unit,
    onReorderMeters: (Int, Int) -> Unit,
    onDeleteMeter: (Meter) -> Unit,
    onMeterClick: (Meter) -> Unit,
    onMenuClick: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingMeter by remember { mutableStateOf<Meter?>(null) }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderState(lazyListState) { from, to ->
        onReorderMeters(from, to)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meters") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Meter")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .weight(1f)
                    .reorderable(reorderState),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(meters, key = { it.id }) { meter ->
                    val isDragging = reorderState.draggedIndex == meters.indexOf(meter)
                    val provider = availableProviders.find { it.id == meter.providerId }

                    SwipeableMeterItem(
                        meter = meter,
                        provider = provider,
                        isDragging = isDragging,
                        dragOffset = if (isDragging) reorderState.dragOffset else 0f,
                        onDelete = { onDeleteMeter(meter) },
                        onEdit = { editingMeter = meter },
                        onClick = { onMeterClick(meter) }
                    )
                }
            }
        }

        if (showAddDialog) {
            MeterDialog(
                title = "Add Meter",
                confirmLabel = "Add",
                availableProviders = availableProviders,
                onDismiss = { showAddDialog = false },
                onConfirm = { name, meterNumber, providerId, icon ->
                    onAddMeter(name, meterNumber, providerId, false, icon)
                    showAddDialog = false
                }
            )
        }

        if (editingMeter != null) {
            MeterDialog(
                title = "Edit Meter",
                confirmLabel = "Save",
                initialMeter = editingMeter,
                availableProviders = availableProviders,
                onDismiss = { editingMeter = null },
                onConfirm = { name, meterNumber, providerId, icon ->
                    onUpdateMeter(editingMeter!!.copy(
                        name = name,
                        meterNumber = meterNumber,
                        providerId = providerId,
                        icon = icon
                    ))
                    editingMeter = null
                }
            )
        }
    }
}

@Composable
fun SwipeableMeterItem(
    meter: Meter,
    provider: TariffIndexItem?,
    isDragging: Boolean,
    dragOffset: Float,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onClick: () -> Unit
) {
    val offsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    // Resolve provider border colour
    val providerColor = remember(provider) {
        try {
            provider?.color?.let { Color(AndroidColor.parseColor(it)) }
        } catch (e: Exception) { null }
    } ?: Color(0x33FFFFFF)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // --- Background layer: edit (left, blue) and delete (right, red) ---
        Box(modifier = Modifier.matchParentSize()) {
            // Edit (blue background) - Revealed when dragging RIGHT
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

            // Delete (red background) - Revealed when dragging LEFT
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
        val velocityTracker = remember { VelocityTracker() }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(if (isDragging) 1f else 0f)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .graphicsLayer {
                    translationY = dragOffset
                    scaleX = if (isDragging) 1.05f else 1.0f
                    scaleY = if (isDragging) 1.05f else 1.0f
                    shadowElevation = if (isDragging) 8f else 0f
                }
                .pointerInput(meter.id) {
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
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = providerColor.copy(alpha = 0.12f).compositeOver(Color.White)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 8.dp else 2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Provider color accent bar
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(6.dp)
                        .background(providerColor)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Emoji icon
                    Text(
                        text = meter.icon,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.width(12.dp))

                    // Name + meter number + provider
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = meter.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        val providerName = provider?.name ?: "Unknown (${meter.providerId})"
                        Text(
                            text = "Meter: ${meter.meterNumber}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Provider: $providerName",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    // Drag handle hint (≡ — reorder hint)
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Drag to reorder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeterDialog(
    title: String,
    confirmLabel: String,
    initialMeter: Meter? = null,
    availableProviders: List<TariffIndexItem>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialMeter?.name ?: "") }
    var meterNumber by remember { mutableStateOf(initialMeter?.meterNumber ?: "") }
    var icon by remember { mutableStateOf(initialMeter?.icon ?: "⚡") }
    var selectedProvider by remember {
        mutableStateOf(availableProviders.find { it.id == initialMeter?.providerId } ?: availableProviders.firstOrNull())
    }
    val currentProviderFound = initialMeter?.let { m -> availableProviders.any { it.id == m.providerId } } ?: true
    var showProviderDialog by remember { mutableStateOf(false) }

    val emojis = listOf("⚡", "🏠", "🏢", "🏭", "🔌", "💡", "🔋")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Meter Nickname (e.g. Home)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = meterNumber,
                    onValueChange = { meterNumber = it },
                    label = { Text("Meter Number") },
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text("Select Icon", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    emojis.forEach { emoji ->
                        FilterChip(
                            selected = icon == emoji,
                            onClick = { icon = emoji },
                            label = { Text(emoji) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                if (!currentProviderFound && initialMeter != null) {
                    Text(
                        "⚠️ Current provider '${initialMeter.providerId}' no longer found. Please select a new one.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text("Select Provider", style = MaterialTheme.typography.labelMedium)
                Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    OutlinedButton(
                        onClick = { showProviderDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val defaultColor = MaterialTheme.colorScheme.primary
                        val color = remember(selectedProvider, defaultColor) {
                            try {
                                selectedProvider?.color?.let { Color(AndroidColor.parseColor(it)) } ?: defaultColor
                            } catch (e: Exception) {
                                defaultColor
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(selectedProvider?.name ?: "Select a provider")
                        }
                    }

                    if (showProviderDialog) {
                        SearchableProviderDialog(
                            providers = availableProviders,
                            onDismiss = { showProviderDialog = false },
                            onSelect = { provider ->
                                selectedProvider = provider
                                showProviderDialog = false
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            val defaultColor = MaterialTheme.colorScheme.primary
            val buttonColor = remember(selectedProvider, defaultColor) {
                try {
                    selectedProvider?.color?.let { Color(AndroidColor.parseColor(it)) } ?: defaultColor
                } catch (e: Exception) {
                    defaultColor
                }
            }

            Button(
                onClick = {
                    selectedProvider?.let { onConfirm(name, meterNumber, it.id, icon) }
                },
                enabled = name.isNotBlank() && meterNumber.isNotBlank() && selectedProvider != null,
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
            ) {
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
