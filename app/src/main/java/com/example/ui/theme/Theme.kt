package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Purple Theme with White Text (Tema Roxo & Letras Brancas)
private val PurpleDarkColorScheme = darkColorScheme(
    primary = PurplePrimaryLight,
    onPrimary = Color.White,
    primaryContainer = PurpleContainer,
    onPrimaryContainer = TextWhite,
    secondary = PurpleSecondary,
    onSecondary = Color.White,
    secondaryContainer = PurpleSecondaryContainer,
    onSecondaryContainer = TextWhite,
    background = PurpleBackground,
    onBackground = TextWhite,
    surface = PurpleSurface,
    onSurface = TextWhite,
    surfaceVariant = PurpleSurfaceVariant,
    onSurfaceVariant = TextWhiteVariant,
    outline = PurpleSurfaceBorder,
    outlineVariant = Color(0xFF6B21A8)
)

private val PurpleLightColorScheme = lightColorScheme(
    primary = PurplePrimary,
    onPrimary = Color.White,
    primaryContainer = PurpleContainerLight,
    onPrimaryContainer = TextWhite,
    secondary = PurpleSecondary,
    onSecondary = Color.White,
    secondaryContainer = PurpleSecondaryContainer,
    onSecondaryContainer = TextWhite,
    background = Color(0xFF190B2E),
    onBackground = TextWhite,
    surface = Color(0xFF261042),
    onSurface = TextWhite,
    surfaceVariant = Color(0xFF38195E),
    onSurfaceVariant = TextWhiteVariant,
    outline = PurpleSurfaceBorder,
    outlineVariant = Color(0xFF6B21A8)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep consistent purple & white branding as requested
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) PurpleDarkColorScheme else PurpleLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
