package net.mamby.health.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = White,
    primaryContainer = Neutral100,
    onPrimaryContainer = NeutralText,
    secondary = NeutralSecondaryText,
    onSecondary = White,
    secondaryContainer = Neutral100,
    onSecondaryContainer = NeutralText,
    tertiary = Info,
    error = Danger,
    background = White,
    onBackground = NeutralText,
    surface = White,
    onSurface = NeutralText,
    surfaceVariant = Neutral50,
    onSurfaceVariant = NeutralSecondaryText,
    surfaceTint = White,
    outline = NeutralOutline,
    outlineVariant = NeutralOutline,
    inverseSurface = NeutralText,
    inverseOnSurface = White,
    surfaceContainerLowest = White,
    surfaceContainerLow = White,
    surfaceContainer = White,
    surfaceContainerHigh = White,
    surfaceContainerHighest = Neutral100,
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    onPrimary = TealDark,
    primaryContainer = DarkElevatedSurface,
    onPrimaryContainer = DarkText,
    secondary = DarkSecondaryText,
    onSecondary = DarkBackground,
    secondaryContainer = DarkElevatedSurface,
    onSecondaryContainer = DarkText,
    tertiary = InfoLight,
    onTertiary = InfoDark,
    error = DangerLight,
    onError = DangerDark,
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkElevatedSurface,
    onSurfaceVariant = DarkSecondaryText,
    surfaceTint = DarkSurface,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    inverseSurface = DarkText,
    inverseOnSurface = DarkBackground,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkElevatedSurface,
    surfaceContainerHighest = DarkElevatedSurface,
)

@Composable
fun HealthVaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
