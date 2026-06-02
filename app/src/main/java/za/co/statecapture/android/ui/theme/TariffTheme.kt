package za.co.statecapture.android.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import za.co.statecapture.android.domain.model.TariffProvider
import android.graphics.Color as AndroidColor

@Composable
fun DynamicTariffTheme(
    provider: TariffProvider? = null,
    isHomeScreen: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        isHomeScreen -> {
            lightColorScheme(
                primary = SA_Green,
                onPrimary = Color.White,
                secondary = SA_Red,
                tertiary = SA_Blue,
                primaryContainer = SA_Gold,
                onPrimaryContainer = Color.Black,
                secondaryContainer = Color(0xFFF1F1F1)
            )
        }
        provider?.color != null -> {
            val brandColor = try {
                Color(AndroidColor.parseColor(provider.color))
            } catch (e: Exception) {
                SA_Green
            }
            lightColorScheme(
                primary = brandColor,
                onPrimary = Color.White,
                secondary = brandColor,
                primaryContainer = brandColor.copy(alpha = 0.1f),
                onPrimaryContainer = brandColor,
                surfaceVariant = brandColor.copy(alpha = 0.05f)
            )
        }
        provider?.type == "eskom" -> {
            lightColorScheme(
                primary = Eskom_Blue,
                onPrimary = Color.White,
                secondary = Color(0xFF1976D2),
                primaryContainer = Eskom_Blue,
                onPrimaryContainer = Color.White,
                surfaceVariant = Color(0xFFE3F2FD)
            )
        }
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

@Composable
fun ProviderThemedBlock(
    provider: TariffProvider?,
    content: @Composable () -> Unit
) {
    if (provider == null) {
        content()
        return
    }

    val brandColor = try {
        Color(AndroidColor.parseColor(provider.color))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    val providerColorScheme = lightColorScheme(
        primary = brandColor,
        onPrimary = Color.White,
        secondary = brandColor,
        onSecondary = Color.White,
        primaryContainer = brandColor.copy(alpha = 0.1f),
        onPrimaryContainer = brandColor,
        surfaceVariant = brandColor.copy(alpha = 0.05f),
        onSurfaceVariant = brandColor
    )

    MaterialTheme(
        colorScheme = providerColorScheme,
        content = content
    )
}
