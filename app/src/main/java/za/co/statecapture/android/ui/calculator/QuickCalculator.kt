package za.co.statecapture.android.ui.calculator

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import za.co.statecapture.android.ui.components.SearchableProviderDialog
import za.co.statecapture.android.ui.theme.*
import za.co.statecapture.android.util.AppConstants
import android.graphics.Color as AndroidColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCalculatorContent(
    viewModel: CalculationViewModel,
    showProviderSelector: Boolean = true
) {
    val uiState by viewModel.uiState.collectAsState()
    var showProviderDialog by remember { mutableStateOf(false) }

    val blocks = uiState.selectedProvider?.periods?.lastOrNull()?.blocks

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Global VAT toggle ──────────────────────────────────────────
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
        Spacer(modifier = Modifier.height(8.dp))

        // ── Provider selector (Quick Calc only) ──────────────────────────
        if (showProviderSelector && uiState.selectedMeter == null) {
            Text("Select Provider", style = MaterialTheme.typography.labelMedium)
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                OutlinedButton(
                    onClick = { showProviderDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val defaultColor = MaterialTheme.colorScheme.primary
                    val color = remember(uiState.selectedIndexItem, defaultColor) {
                        try {
                            val hex = uiState.selectedIndexItem?.color
                            if (hex != null) Color(AndroidColor.parseColor(hex)) else defaultColor
                        } catch (e: Exception) { defaultColor }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(uiState.selectedIndexItem?.name ?: "Select a provider")
                    }
                }
                if (showProviderDialog) {
                    SearchableProviderDialog(
                        providers = uiState.providers,
                        onDismiss = { showProviderDialog = false },
                        onSelect = { viewModel.onProviderSelected(it); showProviderDialog = false }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Visual IBT Progress Bar for "Try it out"
        if (!blocks.isNullOrEmpty()) {
            Text(
                "Block Visualisation:", 
                style = MaterialTheme.typography.labelSmall, 
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            IbtProgressBar(
                blocks = blocks,
                breakdown = emptyList(),
                pendingBreakdown = uiState.result?.result?.blockBreakdown
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Amount input (placed immediately below IbtProgressBar)
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
        Spacer(modifier = Modifier.height(16.dp))

        // Result
        AnimatedContent(
            targetState = uiState.result,
            transitionSpec = { fadeIn() + slideInVertically() togetherWith fadeOut() },
            label = "ResultAnimation"
        ) { displayResult ->
            if (displayResult != null) {
                Column {
                    CalculationSummaryCard(displayResult = displayResult)
                    SmartWarningsSection(uiState.smartWarnings)
                }
            }
        }
    }
}

@Composable
fun ResultCard(displayResult: CalculationDisplayResult) {
    val result = displayResult.result
    val includeVat = displayResult.includeVat
    val mode = displayResult.mode
    val vatAmountCents = displayResult.vatAmountCents
    val totalDisplayCents = if (includeVat) result.totalCostCents + vatAmountCents else result.totalCostCents

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.Top) {
            result.blockBreakdown.forEach { block ->
                val rateDisplay = if (includeVat) block.ratePerKwhCents * AppConstants.VAT_MULTIPLIER else block.ratePerKwhCents
                val costDisplay = if (includeVat) block.costCents * AppConstants.VAT_MULTIPLIER else block.costCents

                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Block ${block.blockIndex}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text("${String.format("%.2f", rateDisplay)}c / unit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                            Text("${String.format("%.2f", block.kwhYield)} kWh", style = MaterialTheme.typography.bodyMedium)
                            Text("R${String.format("%.2f", costDisplay / 100.0)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(0.dp)) {
                if (result.fixedChargeCents > 0) {
                    val fixedChargeDisplay = if (includeVat) result.fixedChargeCents * AppConstants.VAT_MULTIPLIER else result.fixedChargeCents.toDouble()
                    val fixedChargeLabel = if (displayResult.fixedChargeAlreadyPaid) "Monthly Fixed Charge already paid this month: " else "Monthly Fixed Charge: "
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        Text(fixedChargeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("R${String.format("%.2f", fixedChargeDisplay / 100.0)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val totalLabel = if (mode == CalculationMode.RandsToKwh) "Total Yield: " else "Total Cost: "
                    val totalValue = if (mode == CalculationMode.RandsToKwh) "${String.format("%.2f", result.totalKwh)} kWh" else "R${String.format("%.2f", totalDisplayCents / 100.0)}"
                    Text(totalLabel, style = MaterialTheme.typography.bodyLarge)
                    Text(totalValue, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }

            result.errorMessage?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun SmartWarningsSection(warnings: List<SmartWarning>) {
    if (warnings.isEmpty()) return

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        warnings.forEach { warning ->
            val bgColor = when (warning.severity) {
                WarningSeverity.ALERT -> Warning_Alert_Bg
                WarningSeverity.WARNING -> Warning_Warn_Bg
                WarningSeverity.INFO -> Warning_Info_Bg
            }
            val icon = when (warning.severity) {
                WarningSeverity.ALERT -> Icons.Default.Warning
                WarningSeverity.WARNING -> Icons.Default.Info
                WarningSeverity.INFO -> Icons.Default.Info
            }
            val iconColor = when (warning.severity) {
                WarningSeverity.ALERT -> Warning_Alert_Icon
                WarningSeverity.WARNING -> Warning_Warn_Icon
                WarningSeverity.INFO -> Warning_Info_Icon
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = bgColor),
                border = BorderStroke(1.dp, iconColor.copy(alpha = 0.3f))
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(warning.title, style = MaterialTheme.typography.labelLarge, color = iconColor, fontWeight = FontWeight.Bold)
                        Text(warning.message, style = MaterialTheme.typography.bodySmall, color = Color.Black.copy(alpha = 0.8f))
                    }
                }
            }
        }
    }
}
