package za.co.statecapture.android.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import za.co.statecapture.android.ui.theme.*
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.text.font.FontStyle
import za.co.statecapture.android.util.FlashlightManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ChartType { LINE, BAR, PIE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onMenuClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    val context = LocalContext.current
                    val flashlightManager = remember { FlashlightManager(context) }
                    var flashlightEnabled by remember { mutableStateOf(false) }

                    if (flashlightManager.hasFlashlight()) {
                        IconButton(onClick = {
                            flashlightEnabled = !flashlightEnabled
                            flashlightManager.toggleFlashlight(flashlightEnabled)
                        }) {
                            Text(if (flashlightEnabled) "🟡" else "🔦", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Cost & Consumption",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Incl. VAT", style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = uiState.includeVat,
                        onCheckedChange = { viewModel.onIncludeVatToggle(it) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Row 1: This Month So Far — full width
                item(span = { GridItemSpan(2) }) {
                    StatCard(
                        title = "This Month So Far",
                        rand = uiState.thisMonthRand,
                        kwh = uiState.thisMonthKwh,
                        color = MaterialTheme.colorScheme.primary,
                        badge = if (uiState.thisMonthPurchaseCount > 0) {
                            val n = uiState.thisMonthPurchaseCount
                            "$n purchase${if (n == 1) "" else "s"} recorded"
                        } else null
                    )
                }

                // Row 2: Averages — full width
                item(span = { GridItemSpan(2) }) {
                    AveragesCard(
                        dailyRand = uiState.dailyAverageRand,
                        dailyKwh = uiState.dailyAverageKwh,
                        weeklyRand = uiState.weeklyAverageRand,
                        weeklyKwh = uiState.weeklyAverageKwh,
                        monthlyRand = uiState.monthlyAverageRand,
                        monthlyKwh = uiState.monthlyAverageKwh,
                        subtitle = uiState.averagesSubtitle,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                // Row 2: Last 12 Months (Full Width Graph)
                item(span = { GridItemSpan(2) }) {
                    var chartType by remember { mutableStateOf(ChartType.LINE) }
                    GraphCard(
                        title = "Last 12 Months",
                        rand = uiState.last12MonthsRand,
                        kwh = uiState.last12MonthsKwh,
                        history = uiState.monthlyHistory,
                        chartType = chartType,
                        onToggleChart = {
                            chartType = when (chartType) {
                                ChartType.LINE -> ChartType.BAR
                                ChartType.BAR -> ChartType.PIE
                                ChartType.PIE -> ChartType.LINE
                            }
                        },
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                item(span = { GridItemSpan(2) }) {
                    val footerText = remember(uiState.firstPurchaseTimestamp) {
                        uiState.firstPurchaseTimestamp?.let {
                            val dateLabel = SimpleDateFormat("d MMMM yyyy", Locale.US).format(Date(it))
                            "... since we started capturing the state on $dateLabel 🇿🇦"
                        } ?: "... since we started capturing the state 'in the beninging' 🇿🇦"
                    }
                    StatCard(
                        title = "All Time Total",
                        rand = uiState.allTimeRand,
                        kwh = uiState.allTimeKwh,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        footer = footerText
                    )
                }
            }

        }
    }
}

@Composable
fun StatCard(
    title: String,
    rand: Double,
    kwh: Double,
    color: Color,
    footer: String? = null,
    badge: String? = null,
    subtitle: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
                if (badge != null) {
                    Surface(
                        color = color.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            badge,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = color
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "R ${String.format("%.2f", rand)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
            Text(
                "${String.format("%.1f", kwh)} kWh",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontStyle = FontStyle.Italic
                )
            }
            if (footer != null) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    footer,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

@Composable
fun AveragesCard(
    dailyRand: Double,
    dailyKwh: Double,
    weeklyRand: Double,
    weeklyKwh: Double,
    monthlyRand: Double,
    monthlyKwh: Double,
    subtitle: String,
    color: Color = MaterialTheme.colorScheme.secondary
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "Averages",
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            AverageRow(label = "Daily", kwh = dailyKwh, rand = dailyRand)
            Spacer(modifier = Modifier.height(8.dp))
            AverageRow(label = "Weekly", kwh = weeklyKwh, rand = weeklyRand)
            Spacer(modifier = Modifier.height(8.dp))
            AverageRow(label = "Monthly", kwh = monthlyKwh, rand = monthlyRand)

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontStyle = FontStyle.Italic
            )
        }
    }
}

@Composable
private fun AverageRow(label: String, kwh: Double, rand: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${String.format("%.1f", kwh)} kWh @ R ${String.format("%.2f", rand)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun GraphCard(
    title: String,
    rand: Double,
    kwh: Double,
    history: List<MonthData>,
    chartType: ChartType,
    onToggleChart: () -> Unit,
    color: Color
) {
    Card(
        onClick = onToggleChart,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "R ${String.format("%.2f", rand)} | ${String.format("%.1f", kwh)} kWh",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = color.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        chartType.name,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = color
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // The Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (chartType == ChartType.PIE) 210.dp else 200.dp)
            ) {
                if (history.isEmpty()) {
                    Text(
                        "No data available",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    SimpleChart(history = history, chartType = chartType, color = color)
                }
            }
        }
    }
}

val ChartColors = listOf(
    SA_Red,
    SA_Blue,
    SA_Green,
    SA_Gold,
    SA_Black,
    SA_White,
    // Repeating with variations for 12 months
    SA_Red.copy(alpha = 0.7f),
    SA_Blue.copy(alpha = 0.7f),
    SA_Green.copy(alpha = 0.7f),
    SA_Gold.copy(alpha = 0.7f),
    SA_Black.copy(alpha = 0.7f),
    SA_White.copy(alpha = 0.7f)
)

@Composable
fun SimpleChart(history: List<MonthData>, chartType: ChartType, color: Color) {
    if (chartType == ChartType.PIE) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                PieChartCore(history = history)
            }
            Spacer(modifier = Modifier.height(8.dp))
            // 3-column legend
            Row(modifier = Modifier.fillMaxWidth()) {
                val columns = 3
                val rows = (history.size + columns - 1) / columns
                for (c in 0 until columns) {
                    Column(modifier = Modifier.weight(1f)) {
                        for (r in 0 until rows) {
                            val index = c * rows + r
                            if (index < history.size) {
                                LegendItem(index, history[index])
                            }
                        }
                    }
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (chartType == ChartType.LINE) LineChartCore(history, color)
                else BarChartCore(history, color)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                history.forEach { data ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            data.monthName,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            modifier = Modifier.graphicsLayer(rotationZ = -90f)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun LegendItem(index: Int, data: MonthData) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp)
    ) {
        Box(modifier = Modifier.size(8.dp).background(ChartColors[index % ChartColors.size], CircleShape))
        Spacer(modifier = Modifier.width(4.dp))
        Text(data.monthName, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
fun LineChartCore(history: List<MonthData>, color: Color) {
    val maxVal = remember(history) { history.maxOf { it.kwhYield }.coerceAtLeast(1.0) }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val segmentWidth = width / history.size
        val points = history.mapIndexed { index, data ->
            Offset((index + 0.5f) * segmentWidth, height - (data.kwhYield / maxVal * height).toFloat())
        }
        for (i in 0 until points.size - 1) {
            drawLine(color = color, start = points[i], end = points[i + 1], strokeWidth = 2.dp.toPx())
            drawCircle(color = color, radius = 3.dp.toPx(), center = points[i])
        }
        drawCircle(color = color, radius = 3.dp.toPx(), center = points.last())
    }
}

@Composable
fun BarChartCore(history: List<MonthData>, color: Color) {
    val maxVal = remember(history) { history.maxOf { it.kwhYield }.coerceAtLeast(1.0) }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val barWidth = (width / history.size) * 0.7f
        val barSpacing = (width / history.size) * 0.3f
        history.forEachIndexed { index, data ->
            val barHeight = (data.kwhYield / maxVal * height).toFloat()
            drawRect(
                color = color.copy(alpha = 0.7f),
                topLeft = Offset(index.toFloat() * (barWidth + barSpacing) + barSpacing / 2, height - barHeight),
                size = Size(barWidth, barHeight)
            )
        }
    }
}

@Composable
fun PieChartCore(history: List<MonthData>) {
    val totalKwh = history.sumOf { it.kwhYield }.coerceAtLeast(1.0)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val radius = height.coerceAtMost(width)
        var currentAngle = 0f
        history.forEachIndexed { index, data ->
            val sweepAngle = (data.kwhYield / totalKwh * 360.0).toFloat()
            if (sweepAngle > 0) {
                drawArc(
                    color = ChartColors[index % ChartColors.size],
                    startAngle = currentAngle,
                    sweepAngle = sweepAngle,
                    useCenter = true,
                    size = Size(radius, radius),
                    topLeft = Offset((width - radius) / 2, 0f)
                )
            }
            currentAngle += sweepAngle
        }
    }
}
