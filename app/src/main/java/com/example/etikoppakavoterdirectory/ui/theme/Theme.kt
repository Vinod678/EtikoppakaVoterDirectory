package com.example.etikoppakavoterdirectory.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

private val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }
private val LocalBrandColors = staticCompositionLocalOf { LightBrandColors }

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * App theme: fixed teal brand colours in light and dark mode. Dynamic (wallpaper) colour is
 * intentionally not used so that status colours stay consistent and recognisable for field teams.
 */
@Composable
fun EtikoppakaVoterDirectoryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors,
        LocalBrandColors provides if (darkTheme) DarkBrandColors else LightBrandColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/** Access to the app's status colours: `AppTheme.status.favourite` etc. */
object AppTheme {
    val status: StatusColors
        @Composable @ReadOnlyComposable get() = LocalStatusColors.current
    val brand: BrandColors
        @Composable @ReadOnlyComposable get() = LocalBrandColors.current
}
