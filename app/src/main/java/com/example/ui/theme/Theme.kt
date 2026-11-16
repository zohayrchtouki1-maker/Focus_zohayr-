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
    primary = CoralPrimary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = CoralContainerDark,
    onPrimaryContainer = DarkTextPrimary,
    secondary = RoyalBlueChart,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = RoyalBlueChart,
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
    primary = CoralPrimary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = CoralContainerLight,
    onPrimaryContainer = CoralDark,
    secondary = RoyalBlueChart,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = LightTextPrimary,
    tertiary = RoyalBlueChart,
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
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun FocusTodoTheme(
    darkTheme: Boolean = true,
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
