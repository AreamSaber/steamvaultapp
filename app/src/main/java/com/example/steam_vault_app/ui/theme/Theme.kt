package com.example.steam_vault_app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = VaultBlue,
    onPrimary = Cloud50,
    primaryContainer = VaultBlueDark,
    onPrimaryContainer = Cloud50,
    secondary = SignalTeal,
    onSecondary = Night900,
    tertiary = AlertAmber,
    onTertiary = Night900,
    background = Night900,
    onBackground = Cloud50,
    surface = Night800,
    onSurface = Cloud50,
    surfaceVariant = Night700,
    onSurfaceVariant = Slate300,
    error = AlertCoral,
    onError = Night900,
    outline = Slate500,
)

private val LightColorScheme = lightColorScheme(
    primary = VaultBlueDark,
    onPrimary = Cloud50,
    primaryContainer = Color(0xFFD9E6FF),
    onPrimaryContainer = Ink900,
    secondary = SignalTeal,
    onSecondary = Cloud50,
    tertiary = AlertAmber,
    onTertiary = Ink900,
    background = Cloud50,
    onBackground = Ink900,
    surface = Color(0xFFFFFFFF),
    onSurface = Ink900,
    surfaceVariant = Cloud100,
    onSurfaceVariant = Slate500,
    error = AlertCoral,
    onError = Cloud50,
    outline = Color(0xFF98A6BC),
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
        content = content,
    )
}
