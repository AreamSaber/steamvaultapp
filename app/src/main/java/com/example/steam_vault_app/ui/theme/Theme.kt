package com.example.steam_vault_app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = VaultBlue,
    onPrimary = Night950,
    primaryContainer = VaultBlueDark,
    onPrimaryContainer = Cloud50,
    secondary = SignalTeal,
    onSecondary = Night950,
    tertiary = AlertAmber,
    onTertiary = Night950,
    background = Night900,
    onBackground = Cloud50,
    surface = Night850,
    onSurface = Cloud50,
    surfaceVariant = Night700,
    onSurfaceVariant = Slate300,
    surfaceTint = VaultBlue,
    outline = Slate500,
    outlineVariant = Slate700,
    error = AlertCoral,
    onError = Night950,
    errorContainer = Color(0xFF5B241B),
    onErrorContainer = Cloud50,
    surfaceContainerLowest = Night950,
    surfaceContainerLow = Night800,
    surfaceContainer = Night700,
    surfaceContainerHigh = Night600,
    surfaceContainerHighest = Color(0xFF333537),
    surfaceBright = Color(0xFF37393B),
    surfaceDim = Night900,
)

private val LightColorScheme = lightColorScheme(
    primary = VaultBlueDark,
    onPrimary = Cloud50,
    primaryContainer = VaultBlue,
    onPrimaryContainer = Night950,
    secondary = SignalTeal,
    onSecondary = Night950,
    tertiary = AlertAmber,
    onTertiary = Night950,
    background = Cloud50,
    onBackground = Ink900,
    surface = Color.White,
    onSurface = Ink900,
    surfaceVariant = Cloud100,
    onSurfaceVariant = Ink700,
    surfaceTint = VaultBlueDark,
    outline = Cloud200,
    outlineVariant = Cloud100,
    error = AlertCoral,
    onError = Cloud50,
    errorContainer = Color(0xFFFFDDD7),
    onErrorContainer = Ink900,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7FAFD),
    surfaceContainer = Cloud50,
    surfaceContainerHigh = Cloud100,
    surfaceContainerHighest = Color(0xFFEAF1F7),
    surfaceBright = Color.White,
    surfaceDim = Cloud100,
)

@Composable
fun SteamVaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = SteamVaultShapes,
        content = content,
    )
}
