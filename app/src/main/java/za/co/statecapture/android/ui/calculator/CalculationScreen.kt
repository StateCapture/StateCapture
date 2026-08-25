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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import java.text.DecimalFormat
import kotlin.math.roundToInt
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import java.util.Calendar
import za.co.statecapture.android.domain.engine.TariffCalculator
import za.co.statecapture.android.domain.model.TariffProvider
import za.co.statecapture.android.data.Purchase
import za.co.statecapture.android.domain.engine.CalculationResult
import za.co.statecapture.android.domain.engine.BlockYield
import za.co.statecapture.android.util.AppConstants
import za.co.statecapture.android.ui.components.SearchableProviderDialog
import za.co.statecapture.android.ui.theme.ProviderThemedBlock
import za.co.statecapture.android.ui.theme.*
import android.app.DatePickerDialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import android.graphics.Color as AndroidColor
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Menu

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import za.co.statecapture.android.domain.model.TariffBlock

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

    var recordDate by remember(uiState.selectedYearMonth) {
        mutableStateOf(if (isCurrentMonth) LocalDate.now() else uiState.selectedYearMonth.atDay(1))
    }
    var showRecordDatePicker by remember { mutableStateOf(false) }
    var showFbeDatePicker by remember { mutableStateOf(false) }

    var totalDragAmount by remember { mutableFloatStateOf(0f) }

    if (showRecordDatePicker) {
        val calendar = Calendar.getInstance().apply {
            set(recordDate.year, recordDate.monthValue - 1, recordDate.dayOfMonth, 12, 0, 0)
        }
        val context = LocalContext.current
        val datePickerDialog = DatePickerDialog(
            context,
            { _, year, month, day ->
                val chosen = LocalDate.of(year, month + 1, day)
                recordDate = chosen
                viewModel.savePurchase(chosen)
                showRecordDatePicker = false
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            val minCal = Calendar.getInstance().apply {
                set(uiState.selectedYearMonth.year, uiState.selectedYearMonth.monthValue - 1, 1, 0, 0, 0)
            }
            val maxCal = Calendar.getInstance().apply {
                if (isCurrentMonth) {
                    timeInMillis = System.currentTimeMillis()
                } else {
                    val lastDay = uiState.selectedYearMonth.atEndOfMonth().dayOfMonth
                    set(uiState.selectedYearMonth.year, uiState.selectedYearMonth.monthValue - 1, lastDay, 23, 59, 59)
                }
            }
            datePicker.minDate = minCal.timeInMillis
            datePicker.maxDate = maxCal.timeInMillis
            setOnDismissListener { showRecordDatePicker = false }
        }
        DisposableEffect(Unit) {
            datePickerDialog.show()
            onDispose { datePickerDialog.dismiss() }
        }
    }

    if (showFbeDatePicker) {
        val calendar = Calendar.getInstance().apply {
            set(recordDate.year, recordDate.monthValue - 1, recordDate.dayOfMonth, 12, 0, 0)
        }
        val context = LocalContext.current
        val datePickerDialog = DatePickerDialog(
            context,
            { _, year, month, day ->
                val chosen = LocalDate.of(year, month + 1, day)
                recordDate = chosen
                viewModel.claimFreeBlock(chosen)
                showFbeDatePicker = false
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            val minCal = Calendar.getInstance().apply {
                set(uiState.selectedYearMonth.year, uiState.selectedYearMonth.monthValue - 1, 1, 0, 0, 0)
            }
            val maxCal = Calendar.getInstance().apply {
                if (isCurrentMonth) {
                    timeInMillis = System.currentTimeMillis()
                } else {
                    val lastDay = uiState.selectedYearMonth.atEndOfMonth().dayOfMonth
                    set(uiState.selectedYearMonth.year, uiState.selectedYearMonth.monthValue - 1, lastDay, 23, 59, 59)
                }
            }
            datePicker.minDate = minCal.timeInMillis
            datePicker.maxDate = maxCal.timeInMillis
            setOnDismissListener { showFbeDatePicker = false }
        }
        DisposableEffect(Unit) {
            datePickerDialog.show()
            onDispose { datePickerDialog.dismiss() }
        }
    }

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
                .then(
                    if (uiState.selectedMeter != null) {
                        Modifier.pointerInput(uiState.selectedYearMonth) {
                            detectHorizontalDragGestures(
                                onDragStart = { totalDragAmount = 0f },
                                onDragEnd = {
                                    if (totalDragAmount < -100f) {
                                        viewModel.goToNextMonth()
                                    } else if (totalDragAmount > 100f) {
                                        viewModel.goToPreviousMonth()
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    totalDragAmount += dragAmount
                                }
                            )
                        }
                    } else Modifier
                )
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
                        // IBT Progress Bar — always visible when provider has blocks
                        val refDate = if (isCurrentMonth) LocalDate.now() else uiState.selectedYearMonth.atEndOfMonth()
                        val activePeriod = uiState.selectedProvider?.periods?.find { p ->
                            val from = LocalDate.parse(p.validFrom)
                            val to = p.validTo?.let { LocalDate.parse(it) }
                            (!refDate.isBefore(from)) && (to == null || !refDate.isAfter(to))
                        } ?: uiState.selectedProvider?.periods?.lastOrNull()
                        val blocks = activePeriod?.blocks
                        if (!blocks.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Block Distribution:", style = MaterialTheme.typography.labelSmall)
                                val hasPending = uiState.result != null
                                if (hasPending) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            "Existing + Projected",
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                } else if (uiState.cumulativeBreakdown.isNotEmpty()) {
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
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            if (uiState.fixedMonthlyChargeCents > 0) {
                                val fixedChargeAlreadyPaid = uiState.monthlyCumulativeKwh > 0
                                val coveredCents = if (fixedChargeAlreadyPaid) {
                                    uiState.fixedMonthlyChargeCents.toDouble()
                                } else {
                                    uiState.result?.result?.let { res ->
                                        kotlin.math.min(res.totalCostCents, uiState.fixedMonthlyChargeCents.toDouble())
                                    } ?: 0.0
                                }

                                FixedMonthlyChargeBar(
                                    chargeCents = uiState.fixedMonthlyChargeCents,
                                    coveredCents = coveredCents,
                                    includeVat = uiState.includeVat
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            IbtProgressBar(
                                blocks = blocks,
                                breakdown = uiState.cumulativeBreakdown,
                                pendingBreakdown = uiState.result?.result?.blockBreakdown,
                                includeVat = uiState.includeVat
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (uiState.selectedMeter != null) {
                if (uiState.availableFreeKwh > 0) {
                    Button(
                        onClick = { showFbeDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Icon(androidx.compose.material.icons.Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Record Free Basic Electricity claim (${uiState.availableFreeKwh.toInt()} kWh)...", textAlign = TextAlign.Center)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Amount input (placed immediately below top card / visualization)
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
                Spacer(modifier = Modifier.height(12.dp))

                // Mode selector
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = uiState.mode == CalculationMode.RandsToKwh, onClick = { viewModel.onModeChange(CalculationMode.RandsToKwh) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Rands → Units") }
                    SegmentedButton(selected = uiState.mode == CalculationMode.KwhToRands, onClick = { viewModel.onModeChange(CalculationMode.KwhToRands) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Units → Rands") }
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Block Shortcuts ("Quick fill" pills)
                BlockShortcutsRow(
                    uiState = uiState,
                    onShortcutClick = { viewModel.onBlockShortcutClick(it) }
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
                            CalculationSummaryCard(
                                displayResult = displayResult
                            )
                            
                            // Smart Warnings
                            SmartWarningsSection(uiState.smartWarnings)
                            
                            val canRecord = (uiState.result?.result?.totalCostCents ?: -1.0) >= 0 && (uiState.result?.result?.totalKwh ?: 0.0) > 0
                            val recordButtonText = if (isCurrentMonth) "Record Purchase..." else "Record Historical Purchase..."
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { showRecordDatePicker = true }, 
                                modifier = Modifier.fillMaxWidth(),
                                enabled = canRecord
                            ) {
                                Text(recordButtonText)
                            }
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
            provider = uiState.selectedProvider,
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
    val isCurrentMonth = uiState.selectedYearMonth == YearMonth.now()
    val refDate = if (isCurrentMonth) LocalDate.now() else uiState.selectedYearMonth.atEndOfMonth()
    val activePeriod = uiState.selectedProvider?.periods?.find { p ->
        val from = LocalDate.parse(p.validFrom)
        val to = p.validTo?.let { LocalDate.parse(it) }
        (!refDate.isBefore(from)) && (to == null || !refDate.isAfter(to))
    } ?: uiState.selectedProvider?.periods?.lastOrNull()
    val blocks = activePeriod?.blocks ?: return
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
                val isExhausted = uiState.monthlyCumulativeKwh >= (block.maxKwh - AppConstants.BLOCK_EXHAUSTION_TOLERANCE_KWH)
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
                        enabled = !isExhausted,
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
    provider: TariffProvider?,
    includeVat: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Purchase) -> Unit
) {
    val vatMultiplier = AppConstants.VAT_MULTIPLIER
    val vatRate = AppConstants.VAT_RATE
    val context = LocalContext.current
    val calculator = remember { TariffCalculator() }

    // Display state for the VAT toggle inside the dialog
    var dialogIncludeVat by remember { mutableStateOf(includeVat) }

    // Linked / Auto-sync toggle
    var isLinked by remember { mutableStateOf(true) }
    
    // The underlying "true" VAT-exclusive amount (in cents) we are editing
    var currentExclCents by remember { mutableStateOf(purchase.amountCents) }

    // UI state for the text fields
    val displayAmount = if (dialogIncludeVat) (currentExclCents * vatMultiplier) / 100.0 else currentExclCents / 100.0
    var amountText by remember { mutableStateOf(String.format(Locale.US, "%.2f", displayAmount)) }
    var kwhText    by remember { mutableStateOf(String.format(Locale.US, "%.2f", purchase.kwhYield)) }
    var selectedTimestamp by remember { mutableStateOf(purchase.timestamp) }

    val purchaseDate = remember(selectedTimestamp) {
        Instant.ofEpochMilli(selectedTimestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    // Synchronize amountText when toggle changes
    LaunchedEffect(dialogIncludeVat) {
        val newDisplay = if (dialogIncludeVat) (currentExclCents * vatMultiplier) / 100.0 else currentExclCents / 100.0
        amountText = String.format(Locale.US, "%.2f", newDisplay)
    }

    // Recalculate everything when amount changes
    fun onAmountInput(input: String) {
        amountText = input
        val amountVal = input.toDoubleOrNull() ?: return
        currentExclCents = if (dialogIncludeVat) {
            val totalCents = Math.round(amountVal * 100.0).toDouble()
            val vatCents = Math.round(totalCents * (vatRate / vatMultiplier)).toDouble()
            totalCents - vatCents
        } else {
            Math.round(amountVal * 100.0).toDouble()
        }
        
        if (isLinked && provider != null) {
            val result = calculator.calculateYield(provider, currentExclCents, 0.0, purchaseDate)
            if (result.totalKwh > 0) {
                kwhText = String.format(Locale.US, "%.2f", result.totalKwh)
            }
        }
    }

    // Recalculate everything when kWh changes
    fun onKwhInput(input: String) {
        kwhText = input
        val kwhVal = input.toDoubleOrNull() ?: return
        if (isLinked && provider != null) {
            val result = calculator.calculateCost(provider, kwhVal, 0.0, purchaseDate, includeFixedCharge = false)
            currentExclCents = result.totalCostCents
            val newDisplay = if (dialogIncludeVat) (currentExclCents * vatMultiplier) / 100.0 else currentExclCents / 100.0
            amountText = String.format(Locale.US, "%.2f", newDisplay)
        }
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

                // Link / Auto-sync toggle
                if (fieldsEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp).alpha(if (isLinked) 1f else 0.4f),
                                tint = if (isLinked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (isLinked) "Auto-sync units & amount" else "Manual mode (unlinked)",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isLinked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                        TextButton(onClick = { isLinked = !isLinked }) {
                            Text(if (isLinked) "Unlink" else "Link")
                        }
                    }
                }

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
            }
        },
        confirmButton = {
            val kwhVal    = kwhText.toDoubleOrNull()
            Button(
                onClick = {
                    if (kwhVal != null) {
                        val vatCents = if (dialogIncludeVat) {
                            val totalCents = Math.round((amountText.toDoubleOrNull() ?: 0.0) * 100.0).toDouble()
                            Math.round(totalCents * (vatRate / vatMultiplier)).toDouble()
                        } else {
                            Math.round(currentExclCents * vatRate).toDouble()
                        }
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

// ── IBT Progress Bar ──────────────────────────────────────────────────────────

/** Anchor colours for the IBT block gradient: cheap → expensive */
private val IbtColorStart = Color(0xFF4CAF50) // green  (cheapest block)
private val IbtColorEnd   = Color(0xFFE53935) // red    (most expensive block)

/**
 * Returns the appropriate colour for a block at [index] out of [total] blocks,
 * always spanning the full green→red gradient regardless of block count.
 *
 * Examples:
 *   1 block  → green
 *   2 blocks → green, red
 *   3 blocks → green, orange, red
 *   4 blocks → green, amber, deep-orange, red
 */
private fun ibtBlockColor(index: Int, total: Int): Color {
    if (total <= 1) return IbtColorStart
    val fraction = index.toFloat() / (total - 1).toFloat()
    return lerp(IbtColorStart, IbtColorEnd, fraction)
}

@Composable
fun IbtProgressBar(
    blocks: List<TariffBlock>,
    breakdown: List<BlockYield>,
    pendingBreakdown: List<BlockYield>? = null,
    includeVat: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (blocks.isEmpty()) return

    // Quick lookup: blockIndex (1-based) → kWh consumed specifically within that block
    val consumedByBlock = breakdown.associate { it.blockIndex to it.kwhYield }
    val pendingByBlock = pendingBreakdown?.associate { it.blockIndex to it.kwhYield } ?: emptyMap()

    // Dynamic capacity calculation for each block to handle the infinite block cleanly
    val capacities = remember(blocks, consumedByBlock, pendingByBlock) {
        val list = mutableListOf<Double>()
        var prevMax = 0
        blocks.forEachIndexed { index, block ->
            val isInfinite = block.maxKwh >= 999_999
            val consumed = (consumedByBlock[index + 1] ?: 0.0) + (pendingByBlock[index + 1] ?: 0.0)
            if (isInfinite) {
                val prevCapacity = if (index > 0) list[index - 1] else 100.0
                val cap = maxOf(consumed, prevCapacity).coerceAtLeast(1.0)
                list.add(cap)
            } else {
                val cap = (block.maxKwh - prevMax).toDouble().coerceAtLeast(1.0)
                list.add(cap)
                prevMax = block.maxKwh
            }
        }
        list
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Segmented bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            blocks.forEachIndexed { index, _ ->
                val blockCapacity = capacities[index]
                val existingConsumed = consumedByBlock[index + 1] ?: 0.0
                val pendingConsumed = pendingByBlock[index + 1] ?: 0.0
                val totalConsumed = existingConsumed + pendingConsumed

                val existingFraction = (existingConsumed / blockCapacity).coerceIn(0.0, 1.0).toFloat()
                val totalFraction = (totalConsumed / blockCapacity).coerceIn(0.0, 1.0).toFloat()

                val animatedExistingFraction by animateFloatAsState(
                    targetValue = existingFraction,
                    animationSpec = tween(durationMillis = 600),
                    label = "block_existing_fill_$index"
                )
                val animatedTotalFraction by animateFloatAsState(
                    targetValue = totalFraction,
                    animationSpec = tween(durationMillis = 600),
                    label = "block_total_fill_$index"
                )

                val blockColor = ibtBlockColor(index, blocks.size)
                val isFinalUnlimitedBlock = blocks[index].maxKwh >= 999_999
                val shape = when {
                    blocks.size == 1 -> RoundedCornerShape(6.dp)
                    index == 0 -> RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 2.dp, bottomEnd = 2.dp)
                    index == blocks.lastIndex -> RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 6.dp, bottomEnd = 6.dp)
                    else -> RoundedCornerShape(2.dp)
                }

                Box(
                    modifier = Modifier
                        .weight(1f) // Equal width for all block segments
                        .fillMaxHeight()
                        .clip(shape)
                        .background(blockColor.copy(alpha = 0.18f))
                ) {
                    // Projected/Pending Layer (Total Fill)
                    if (animatedTotalFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedTotalFraction)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            blockColor.copy(alpha = 0.35f),
                                            blockColor.copy(alpha = 0.50f)
                                        )
                                    )
                                )
                        )
                    }

                    // Existing Solid Layer
                    if (animatedExistingFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedExistingFraction)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            blockColor.copy(alpha = 0.75f),
                                            blockColor
                                        )
                                    )
                                )
                        )
                    }

                    // Checkmark icons
                    if (animatedExistingFraction >= 0.999f) {
                        Text(
                            text = "✓",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else if (animatedTotalFraction >= 0.999f && pendingConsumed > 0) {
                        Text(
                            text = "+✓",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    // Infinity indicator on the right edge of the final unlimited block
                    if (isFinalUnlimitedBlock) {
                        Text(
                            text = "∞",
                            color = if (animatedTotalFraction > 0.8f) Color.White.copy(alpha = 0.9f) else blockColor.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 5.dp)
                        )
                    }
                }
            }
        }

        // Labels below each segment
        Spacer(modifier = Modifier.height(5.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            blocks.forEachIndexed { index, block ->
                val blockCapacity = capacities[index]
                val existingConsumed = consumedByBlock[index + 1] ?: 0.0
                val pendingConsumed = pendingByBlock[index + 1] ?: 0.0
                val blockColor = ibtBlockColor(index, blocks.size)
                val isFinalUnlimitedBlock = block.maxKwh >= 999_999

                Column(
                    modifier = Modifier.weight(1f), // Equal width alignment for labels
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Block ${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = blockColor,
                        fontWeight = FontWeight.Bold
                    )

                    val labelText = if (pendingConsumed > 0) {
                        if (isFinalUnlimitedBlock) {
                            if (existingConsumed > 0) "${String.format(Locale.US, "%.0f", existingConsumed)}+${String.format(Locale.US, "%.0f", pendingConsumed)}/∞"
                            else "${String.format(Locale.US, "%.0f", pendingConsumed)}/∞"
                        } else {
                            if (existingConsumed > 0) "${String.format(Locale.US, "%.0f", existingConsumed)}+${String.format(Locale.US, "%.0f", pendingConsumed)}/${blockCapacity.toInt()}"
                            else "${String.format(Locale.US, "%.0f", pendingConsumed)}/${blockCapacity.toInt()}"
                        }
                    } else {
                        if (isFinalUnlimitedBlock) {
                            "${String.format(Locale.US, "%.0f", existingConsumed)}/∞"
                        } else {
                            "${String.format(Locale.US, "%.0f", existingConsumed)}/${blockCapacity.toInt()}"
                        }
                    }

                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (pendingConsumed > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        fontWeight = if (pendingConsumed > 0) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )

                    val baseRateCents = block.ratePerKwhCents
                    val displayRateCents = if (includeVat) baseRateCents * AppConstants.VAT_MULTIPLIER else baseRateCents
                    Text(
                        text = if (baseRateCents == 0.0) "Free" else "R${String.format(Locale.US, "%.2f", displayRateCents / 100.0)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = blockColor.copy(alpha = 0.85f),
                        fontWeight = if (baseRateCents == 0.0) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun CalculationSummaryCard(
    displayResult: CalculationDisplayResult,
    modifier: Modifier = Modifier
) {
    val result = displayResult.result
    val includeVat = displayResult.includeVat
    val mode = displayResult.mode
    val vatAmountCents = displayResult.vatAmountCents
    val totalDisplayCents = if (includeVat) result.totalCostCents + vatAmountCents else result.totalCostCents

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (mode == CalculationMode.RandsToKwh) "Estimated Units Yield" else "Total Purchase Cost",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (mode == CalculationMode.RandsToKwh) {
                            "${String.format(Locale.US, "%.1f", result.totalKwh)} kWh"
                        } else {
                            "R ${String.format(Locale.US, "%.2f", totalDisplayCents / 100.0)}"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (mode == CalculationMode.RandsToKwh) "Total Cost (${if (includeVat) "incl. VAT" else "excl. VAT"})" else "Total Yield",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (mode == CalculationMode.RandsToKwh) {
                            "R ${String.format(Locale.US, "%.2f", totalDisplayCents / 100.0)}"
                        } else {
                            "${String.format(Locale.US, "%.1f", result.totalKwh)} kWh"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (result.fixedChargeCents > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                val fixedChargeDisplay = if (includeVat) result.fixedChargeCents * AppConstants.VAT_MULTIPLIER else result.fixedChargeCents.toDouble()
                val fixedChargeLabel = if (displayResult.fixedChargeAlreadyPaid) "Fixed charge already paid this month" else "Includes monthly fixed charge"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(fixedChargeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    Text("R${String.format(Locale.US, "%.2f", fixedChargeDisplay / 100.0)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}
