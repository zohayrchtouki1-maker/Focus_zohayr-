package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val FocusDarkColorScheme = darkColorScheme(
    primary = RoyalBlueDarkTheme,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = CoralContainerDark,
    onPrimaryContainer = Color(0xFFE3E3E3),
    secondary = RoyalBlueAccentDark,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkCancelButtonText,
    tertiary = RoyalBlueDarkTheme,
    onTertiary = Color(0xFFFFFFFF),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = PriorityHighColor
)

private val FocusLightColorScheme = lightColorScheme(
    primary = RoyalBlueLightTheme,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = CoralContainerLight,
    onPrimaryContainer = Color(0xFF174EA6),
    secondary = RoyalBlueLightTheme,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = LightTextPrimary,
    tertiary = RoyalBlueLightTheme,
    onTertiary = Color(0xFFFFFFFF),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline,
    outlineVariant = LightSurfaceVariant,
    error = PriorityHighColor
)

val FocusShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FocusTodoTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) FocusDarkColorScheme else FocusLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = FocusTodoTypography,
        shapes = FocusShapes,
        content = content
    )
}
