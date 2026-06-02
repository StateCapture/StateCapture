package za.co.statecapture.android.ui.tariffs

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.co.statecapture.android.util.AppConstants
import za.co.statecapture.android.ui.components.SearchableProviderDialog
import android.graphics.Color as AndroidColor
import za.co.statecapture.android.domain.model.TariffProvider
import androidx.compose.foundation.BorderStroke
import za.co.statecapture.android.ui.calculator.CalculationViewModel
import za.co.statecapture.android.ui.calculator.QuickCalculatorContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TariffInfoScreen(
    viewModel: TariffViewModel,
    calcViewModel: CalculationViewModel,
    onMenuClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showProviderDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tariffs") },
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Electricity tariffs in South Africa are usually 'Incline Block Tariffs' (IBT).",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "This means the more you buy in a calendar month, the more you pay per unit.",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("Explore Tariffs", style = MaterialTheme.typography.titleLarge)
            Text(
                "Select a provider to see their specific block structure and rates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedButton(
                onClick = { showProviderDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                val defaultColor = MaterialTheme.colorScheme.primary
                val providerColor = remember(uiState.selectedProvider, defaultColor) {
                    try {
                        uiState.selectedProvider?.color?.let { Color(AndroidColor.parseColor(it)) } ?: defaultColor
                    } catch (e: Exception) {
                        defaultColor
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).background(providerColor, CircleShape))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(uiState.selectedProvider?.name ?: "Select Provider")
                }
            }

            if (showProviderDialog) {
                SearchableProviderDialog(
                    providers = uiState.providers,
                    onDismiss = { showProviderDialog = false },
                    onSelect = {
                        viewModel.onProviderSelected(it)
                        calcViewModel.onProviderSelected(it)
                        showProviderDialog = false
                    }
                )
            }

            uiState.selectedProvider?.let { provider ->
                Spacer(modifier = Modifier.height(24.dp))
                val defaultColor = MaterialTheme.colorScheme.primary
                val providerColor = remember(provider, defaultColor) {
                    try {
                        provider.color?.let { Color(AndroidColor.parseColor(it)) } ?: defaultColor
                    } catch (e: Exception) {
                        defaultColor
                    }
                }
                TariffDetailCard(provider, providerColor)

                Spacer(modifier = Modifier.height(32.dp))
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                
                Text(
                    "Try it out", 
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Use the quick calculator below to see how these rates apply to your purchase.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                QuickCalculatorContent(
                    viewModel = calcViewModel,
                    showProviderSelector = false
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                text = AppConstants.APP_DISCLAIMER,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 24.dp)
            )
        }
    }
}

@Composable
fun TariffDetailCard(
    provider: TariffProvider,
    accentColor: Color
) {
    val period = provider.periods.firstOrNull() ?: return
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = accentColor.copy(alpha = 0.05f)
        ),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = provider.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Text(
                text = "Type: ${provider.type.replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.labelSmall
            )
            
            if (period.fixedMonthlyChargeCents > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Fixed Monthly Charge: R${String.format("%.2f", period.fixedMonthlyChargeCents / 100.0)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = accentColor.copy(alpha = 0.2f)
            )
            Text(
                text = "Block Rates (Excl. VAT):", 
                style = MaterialTheme.typography.labelMedium,
                color = accentColor
            )
            
            period.blocks.forEachIndexed { index, block ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val rangeText = if (block.maxKwh > 100000) {
                        "Block ${index + 1}: > ${block.minKwh} kWh"
                    } else {
                        "Block ${index + 1}: ${block.minKwh}-${block.maxKwh} kWh"
                    }
                    Text(rangeText, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${block.ratePerKwhCents}c",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
            }
            
        }
    }
}
