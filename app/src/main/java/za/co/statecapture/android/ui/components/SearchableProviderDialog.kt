package za.co.statecapture.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import za.co.statecapture.android.domain.model.TariffProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchableProviderDialog(
    providers: List<TariffProvider>,
    onDismiss: () -> Unit,
    onSelect: (TariffProvider) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredProviders = remember(searchQuery, providers) {
        providers.filter { 
            it.name.contains(searchQuery, ignoreCase = true) || 
            it.type.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Provider") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search (e.g. Cape Town)") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (filteredProviders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No providers found", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(filteredProviders) { provider ->
                            ProviderListItem(
                                provider = provider,
                                onClick = { onSelect(provider) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun ProviderListItem(
    provider: TariffProvider,
    onClick: () -> Unit
) {
    val defaultColor = MaterialTheme.colorScheme.primary
    val color = remember(provider) {
        try {
            if (provider.color != null) Color(android.graphics.Color.parseColor(provider.color)) else defaultColor
        } catch (e: Exception) {
            defaultColor
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(provider.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                provider.type.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
