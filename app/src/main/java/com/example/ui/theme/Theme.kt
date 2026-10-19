package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = RoomyCoral,
    onPrimary = RoomyTextMain,
    primaryContainer = RoomyCoralLight,
    onPrimaryContainer = RoomyTextMain,
    secondary = RoomyLavender,
    onSecondary = Color.White,
    secondaryContainer = RoomyLavenderLight,
    onSecondaryContainer = RoomyTextMain,
    tertiary = RoomyCompress,
    background = RoomyBackground,
    onBackground = RoomyTextMain,
    surface = RoomyBackground,
    onSurface = RoomyTextMain,
    surfaceVariant = RoomyCardBg,
    onSurfaceVariant = RoomyTextSecondary,
    outline = RoomyDivider,
    error = RoomyDelete,
    onError = Color.White
)

@Composable
fun RoomyTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
