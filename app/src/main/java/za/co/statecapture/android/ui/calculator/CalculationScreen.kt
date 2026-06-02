package za.co.statecapture.android.ui.calculator

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import java.text.DecimalFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import android.graphics.Color as AndroidColor
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import za.co.statecapture.android.data.Purchase
import za.co.statecapture.android.domain.engine.CalculationResult
import za.co.statecapture.android.util.AppConstants
import za.co.statecapture.android.ui.components.SearchableProviderDialog
import za.co.statecapture.android.ui.theme.ProviderThemedBlock
import za.co.statecapture.android.ui.theme.*
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import java.util.Calendar
import android.app.DatePickerDialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextAlign

private val SaBlue = SA_Blue
private val SaRed  = SA_Red
private const val SWIPE_THRESHOLD_DP = 80

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculationScreen(
    viewModel: CalculationViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showProviderDialog by remember { mutableStateOf(false) }
    var editingPurchase by remember { mutableStateOf<Purchase?>(null) }

    val isCurrentMonth = uiState.selectedYearMonth == YearMonth.now()
    val monthLabel = uiState.selectedYearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.selectedMeter != null) {
                        Column {
                            Text("Meter", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(uiState.selectedMeter?.icon ?: "⚡", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(end = 8.dp))
                                Text(uiState.selectedMeter?.name ?: "", style = MaterialTheme.typography.titleLarge)
                            }
                            Text(uiState.selectedProvider?.name ?: "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        }
                    } else {
                        Text("Quick Calculation")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ── Month navigation (meter mode only) ──────────────────────────
            if (uiState.selectedMeter != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { viewModel.goToPreviousMonth() }) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous month")
                        }
                        Text(
                            text = monthLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { viewModel.goToNextMonth() },
                            enabled = !isCurrentMonth
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = "Next month",
                                tint = if (!isCurrentMonth) MaterialTheme.colorScheme.onSurface
                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ── Global VAT toggle & Input Section ──────────────────────────────────────────
            if (uiState.selectedMeter == null) {
                QuickCalculatorContent(viewModel = viewModel)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Include 15% VAT in totals", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = uiState.includeVat,
                        onCheckedChange = { viewModel.onIncludeVatChange(it) },
                        modifier = Modifier.scale(0.8f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // ── Cumulative block tracking card ───────────────────────────────
            if (uiState.selectedMeter != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Monthly Summary — $monthLabel", style = MaterialTheme.typography.labelMedium)
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val totalSpend = if (uiState.includeVat) 
                                    (uiState.monthlyCumulativeAmountCents + uiState.monthlyCumulativeVatCents) / 100.0 
                                else 
                                    uiState.monthlyCumulativeAmountCents / 100.0

                                Text(
                                    "R ${String.format("%.2f", totalSpend)}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black
                                )
                                Text(if (uiState.includeVat) "Total Spend (incl. VAT)" else "Total Spend (excl. VAT)", style = MaterialTheme.typography.labelSmall)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${String.format("%.1f", uiState.monthlyCumulativeKwh)} kWh",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text("Total Units", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (uiState.cumulativeBreakdown.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Block Distribution:", style = MaterialTheme.typography.labelSmall)
                                Surface(
                                    color = Success_Green.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "Block Tracking ON",
                                        color = Success_Green,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            uiState.cumulativeBreakdown.forEach { block ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Block ${block.blockIndex}", style = MaterialTheme.typography.bodySmall)
                                    Text("${String.format("%.1f", block.kwhYield)} kWh", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (uiState.selectedMeter != null) {
                if (isCurrentMonth) {
                    if (uiState.availableFreeKwh > 0) {
                        Button(
                            onClick = { viewModel.claimFreeBlock() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Icon(androidx.compose.material.icons.Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Record that I already claimed Free Basic Electricity (${uiState.availableFreeKwh.toInt()} kWh)", textAlign = TextAlign.Center)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    } else {
                        // Mode selector
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(selected = uiState.mode == CalculationMode.RandsToKwh, onClick = { viewModel.onModeChange(CalculationMode.RandsToKwh) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Rands → Units") }
                            SegmentedButton(selected = uiState.mode == CalculationMode.KwhToRands, onClick = { viewModel.onModeChange(CalculationMode.KwhToRands) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Units → Rands") }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        // Block Shortcuts
                        BlockShortcutsRow(
                            uiState = uiState,
                            onShortcutClick = { viewModel.onBlockShortcutClick(it) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
        
                        // Amount input
                        OutlinedTextField(
                            value = uiState.inputAmount,
                            onValueChange = { viewModel.onInputAmountChange(it) },
                            label = { 
                                val type = if (uiState.mode == CalculationMode.RandsToKwh) "Purchase Amount" else "Number of Units"
                                val vatInfo = if (uiState.mode == CalculationMode.RandsToKwh) {
                                    if (uiState.includeVat) " (incl. VAT)" else " (excl. VAT)"
                                } else ""
                                Text("$type$vatInfo")
                            },
                            prefix = { if (uiState.mode == CalculationMode.RandsToKwh) Text("R ") },
                            suffix = { if (uiState.mode == CalculationMode.KwhToRands) Text(" kWh") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(16.dp))
        
                        // Result
                        AnimatedContent(
                            targetState = uiState.result,
                            transitionSpec = { fadeIn() + slideInVertically() togetherWith fadeOut() },
                            label = "ResultAnimation"
                        ) { displayResult ->
                            if (displayResult != null) {
                                Column {
                                    ProviderThemedBlock(provider = uiState.selectedProvider) {
                                        ResultCard(displayResult)
                                    }
                                    
                                    // Smart Warnings
                                    SmartWarningsSection(uiState.smartWarnings)
                                    
                                    // Only allow recording in the current month
                                    val canRecord = (uiState.result?.result?.totalCostCents ?: -1.0) >= 0 && (uiState.result?.result?.totalKwh ?: 0.0) > 0
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { viewModel.savePurchase() }, 
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = canRecord
                                    ) {
                                        Text("Record this Purchase")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Message for past months
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Lock, 
                                contentDescription = null, 
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Recording disabled for past months",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── Purchase history ─────────────────────────────────────────────
            if (uiState.selectedMeter != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Purchases — $monthLabel", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Sort controls
                SortBar(
                    currentField = uiState.sortField,
                    currentDirection = uiState.sortDirection,
                    onSortChange = { viewModel.onSortChange(it) }
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (uiState.recentPurchases.isEmpty()) {
                    Text(
                        "No purchases recorded for $monthLabel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    uiState.recentPurchases.forEach { purchase ->
                        SwipeablePurchaseItem(
                            purchase = purchase,
                            includeVat = uiState.includeVat,
                            providerColor = uiState.selectedProvider?.color,
                            onEdit = { editingPurchase = it },
                            onDelete = { viewModel.deletePurchase(it.id) }
                        )
                    }
                }
            }
        }
    }

    // ── Edit purchase dialog ─────────────────────────────────────────────────
    if (editingPurchase != null) {
        EditPurchaseDialog(
            purchase = editingPurchase!!,
            includeVat = uiState.includeVat,
            onDismiss = { editingPurchase = null },
            onConfirm = { updated ->
                viewModel.editPurchase(updated)
                editingPurchase = null
            }
        )
    }
}

// ── Sort bar ─────────────────────────────────────────────────────────────────

@Composable
fun SortBar(
    currentField: SortField,
    currentDirection: SortDirection,
    onSortChange: (SortField) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SortChip("Date", SortField.DATE, currentField, currentDirection, onSortChange)
        SortChip("Amount", SortField.AMOUNT, currentField, currentDirection, onSortChange)
        SortChip("Units", SortField.UNITS, currentField, currentDirection, onSortChange)
    }
}

@Composable
fun SortChip(
    label: String,
    field: SortField,
    currentField: SortField,
    currentDirection: SortDirection,
    onSortChange: (SortField) -> Unit
) {
    val isSelected = currentField == field
    FilterChip(
        selected = isSelected,
        onClick = { onSortChange(field) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        trailingIcon = if (isSelected) {
            {
                Icon(
                    imageVector = if (currentDirection == SortDirection.DESC) Icons.Default.ArrowDropDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else null
    )
}

// ── Swipeable purchase item ───────────────────────────────────────────────────

@Composable
fun SwipeablePurchaseItem(
    purchase: Purchase,
    includeVat: Boolean,
    providerColor: String?,
    onEdit: (Purchase) -> Unit,
    onDelete: (Purchase) -> Unit
) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val totalCents = if (includeVat) purchase.amountCents + purchase.vatAmountCents else purchase.amountCents
    val dateStr = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        .format(Date(purchase.timestamp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        // Background reveal
        if (offsetX.value != 0f) {
            Row(modifier = Modifier.matchParentSize().clip(RoundedCornerShape(8.dp))) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().background(SaBlue),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.White, modifier = Modifier.padding(start = 16.dp))
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().background(SaRed),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White, modifier = Modifier.padding(end = 16.dp))
                }
            }
        }

        // Foreground card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(purchase.id) {
                    val threshold = SWIPE_THRESHOLD_DP * density
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                when {
                                    offsetX.value > threshold -> { onEdit(purchase); offsetX.animateTo(0f, spring()) }
                                    offsetX.value < -threshold -> { onDelete(purchase); offsetX.animateTo(0f, spring()) }
                                    else -> offsetX.animateTo(0f, spring())
                                }
                            }
                        },
                        onDragCancel = { scope.launch { offsetX.animateTo(0f, spring()) } },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                offsetX.snapTo((offsetX.value + dragAmount).coerceIn(-threshold * 1.5f, threshold * 1.5f))
                            }
                        }
                    )
                },
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = providerColor?.let { Color(AndroidColor.parseColor(it)).copy(alpha = 0.12f).compositeOver(Color.White) }
                    ?: MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Provider color accent bar
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(providerColor?.let { Color(AndroidColor.parseColor(it)) } ?: MaterialTheme.colorScheme.primary)
                )

                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (totalCents <= 0.0) {
                            Text(
                                "Free Basic Electricity",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                "R${String.format("%.2f", totalCents / 100.0)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${String.format("%.1f", purchase.kwhYield)} kWh",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Icon(
                            Icons.Default.Menu, 
                            contentDescription = null, 
                            modifier = Modifier.size(16.dp).alpha(0.3f)
                        )
                    }
                }
            }
        }
    }
}

// ── Edit purchase dialog ─────────────────────────────────────────────────────

@Composable
fun BlockShortcutsRow(
    uiState: CalculationUiState,
    onShortcutClick: (Double) -> Unit
) {
    val blocks = uiState.selectedProvider?.periods?.lastOrNull()?.blocks ?: return
    if (blocks.size <= 1) return

    Column {
        Text(
            "Quick fill to end of block:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            blocks.dropLast(1).forEach { block ->
                val isExhausted = uiState.monthlyCumulativeKwh >= block.maxKwh
                FilterChip(
                    selected = false,
                    onClick = { onShortcutClick(block.maxKwh.toDouble()) },
                    label = { Text("Block ${blocks.indexOf(block) + 1}", style = MaterialTheme.typography.labelSmall) },
                    enabled = !isExhausted,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.primary,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = false,
                        borderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
 fun EditPurchaseDialog(
    purchase: Purchase,
    includeVat: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Purchase) -> Unit
) {
    val vatMultiplier = AppConstants.VAT_MULTIPLIER
    val vatRate = AppConstants.VAT_RATE
    val context = LocalContext.current

    // Display state for the VAT toggle inside the dialog
    var dialogIncludeVat by remember { mutableStateOf(includeVat) }
    
    // The underlying "true" VAT-exclusive amount (in cents) we are editing
    var currentExclCents by remember { mutableStateOf(purchase.amountCents) }
    
    // Effective base rate (cents per kWh, excl VAT) derived from the original purchase
    val effectiveRateCents = if (purchase.kwhYield > 0) purchase.amountCents / purchase.kwhYield else 0.0

    // UI state for the text fields
    val displayAmount = if (dialogIncludeVat) (currentExclCents * vatMultiplier) / 100.0 else currentExclCents / 100.0
    var amountText by remember { mutableStateOf(String.format(Locale.US, "%.2f", displayAmount)) }
    var kwhText    by remember { mutableStateOf(String.format(Locale.US, "%.2f", purchase.kwhYield)) }
    var selectedTimestamp by remember { mutableStateOf(purchase.timestamp) }

    // Synchronize amountText when toggle changes
    LaunchedEffect(dialogIncludeVat) {
        val newDisplay = if (dialogIncludeVat) (currentExclCents * vatMultiplier) / 100.0 else currentExclCents / 100.0
        amountText = String.format("%.2f", newDisplay)
    }

    // Recalculate everything when amount changes
    fun onAmountInput(input: String) {
        amountText = input
        val amountVal = input.toDoubleOrNull() ?: return
        currentExclCents = if (dialogIncludeVat) (amountVal * 100.0) / vatMultiplier else amountVal * 100.0
        
        if (effectiveRateCents > 0) {
            val kwh = currentExclCents / effectiveRateCents
            kwhText = String.format(Locale.US, "%.2f", kwh)
        }
    }

    // Recalculate everything when kWh changes
    fun onKwhInput(input: String) {
        kwhText = input
        val kwhVal = input.toDoubleOrNull() ?: return
        currentExclCents = kwhVal * effectiveRateCents
        val newDisplay = if (dialogIncludeVat) (currentExclCents * vatMultiplier) / 100.0 else currentExclCents / 100.0
        amountText = String.format(Locale.US, "%.2f", newDisplay)
    }

    // Date picker dialog setup
    val calendar = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, day ->
            val picked = Calendar.getInstance().apply {
                set(year, month, day, 12, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            selectedTimestamp = picked.timeInMillis
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).also { it.datePicker.maxDate = System.currentTimeMillis() }

    val displayDate = remember(selectedTimestamp) {
        SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
            .format(Date(selectedTimestamp))
    }

    val isFbeClaim = purchase.amountCents <= 0.0
    val fieldsEnabled = !isFbeClaim

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Purchase") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                if (isFbeClaim) {
                    Text(
                        "This is a Free Basic Electricity claim. The amount and units cannot be modified, but you can change the date.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                // VAT Toggle
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp)).padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Include 15% VAT", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = dialogIncludeVat, onCheckedChange = { dialogIncludeVat = it }, enabled = fieldsEnabled)
                }

                // Amount field
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { onAmountInput(it) },
                    label = { Text(if (dialogIncludeVat) "Amount incl. VAT (R)" else "Amount excl. VAT (R)") },
                    prefix = { Text("R ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = fieldsEnabled,
                    modifier = Modifier.fillMaxWidth()
                )

                // kWh field
                OutlinedTextField(
                    value = kwhText,
                    onValueChange = { onKwhInput(it) },
                    label = { Text("Units (kWh)") },
                    suffix = { Text(" kWh") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = fieldsEnabled,
                    modifier = Modifier.fillMaxWidth()
                )

                // Date picker button
                OutlinedButton(
                    onClick = { datePickerDialog.show() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(displayDate)
                }
                
                Text(
                    "Note: Editing amount or units uses the original purchase's effective rate to keep them in sync.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        },
        confirmButton = {
            val kwhVal    = kwhText.toDoubleOrNull()
            Button(
                onClick = {
                    if (kwhVal != null) {
                        val vatCents = currentExclCents * vatRate
                        onConfirm(purchase.copy(
                            amountCents    = currentExclCents,
                            vatAmountCents = vatCents,
                            kwhYield       = kwhVal,
                            timestamp      = selectedTimestamp
                        ))
                    }
                },
                enabled = kwhVal != null && kwhVal > 0 && currentExclCents >= 0
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

