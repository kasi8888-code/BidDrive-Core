package com.biddrive.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RapidoColorScheme = lightColorScheme(
    primary = RapidoYellow,
    onPrimary = TextOnYellow,
    primaryContainer = RapidoYellowLight,
    onPrimaryContainer = TextOnYellow,
    secondary = BargainGreen,
    onSecondary = PureWhite,
    secondaryContainer = BargainGreenLight,
    onSecondaryContainer = BargainGreenDark,
    tertiary = RapidoOrange,
    background = PureWhite,
    onBackground = TextPrimary,
    surface = PureWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = PureWhite,
    errorContainer = ErrorRedLight,
    outline = BorderSubtle
)

@Composable
fun BidDriveTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RapidoColorScheme,
        content = content
    )
}
