package net.mamby.health.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = LightSurface,
    primaryContainer = TealLight,
    onPrimaryContainer = TealDark,
    secondary = Coral,
    tertiary = Info,
    error = Danger,
    background = LightBackground,
    surface = LightSurface,
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    onPrimary = TealDark,
    primaryContainer = TealDark,
    onPrimaryContainer = TealLight,
    secondary = Coral,
    tertiary = Info,
    error = Danger,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceContainer = DarkElevatedSurface,
)

@Composable
fun HealthVaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
