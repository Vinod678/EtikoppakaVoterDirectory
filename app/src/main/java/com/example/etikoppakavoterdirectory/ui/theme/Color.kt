package com.example.etikoppakavoterdirectory.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Deep teal brand palette on restrained light-neutral surfaces (Material 3 tonal roles).

val LightColors = lightColorScheme(
    primary = Color(0xFF00696A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB9ECEA),
    onPrimaryContainer = Color(0xFF002020),
    inversePrimary = Color(0xFF80D5D4),
    secondary = Color(0xFF4A6363),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E8E7),
    onSecondaryContainer = Color(0xFF051F1F),
    tertiary = Color(0xFF3F5F90),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD6E3FF),
    onTertiaryContainer = Color(0xFF001B3E),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8FAFA),
    onBackground = Color(0xFF181C1C),
    surface = Color(0xFFF8FAFA),
    onSurface = Color(0xFF181C1C),
    surfaceVariant = Color(0xFFDDE5E4),
    onSurfaceVariant = Color(0xFF3F4948),
    surfaceTint = Color(0xFF00696A),
    inverseSurface = Color(0xFF2D3131),
    inverseOnSurface = Color(0xFFEFF1F1),
    outline = Color(0xFF6F7979),
    outlineVariant = Color(0xFFC4CDCC),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF8FAFA),
    surfaceDim = Color(0xFFD8DBDB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F5F5),
    surfaceContainer = Color(0xFFECEFEF),
    surfaceContainerHigh = Color(0xFFE6E9E9),
    surfaceContainerHighest = Color(0xFFE0E3E3),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF80D5D4),
    onPrimary = Color(0xFF003737),
    primaryContainer = Color(0xFF004F50),
    onPrimaryContainer = Color(0xFF9CF1F0),
    inversePrimary = Color(0xFF00696A),
    secondary = Color(0xFFB0CCCB),
    onSecondary = Color(0xFF1B3534),
    secondaryContainer = Color(0xFF324B4B),
    onSecondaryContainer = Color(0xFFCCE8E7),
    tertiary = Color(0xFFA9C7FF),
    onTertiary = Color(0xFF07305F),
    tertiaryContainer = Color(0xFF254777),
    onTertiaryContainer = Color(0xFFD6E3FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101414),
    onBackground = Color(0xFFE0E3E3),
    surface = Color(0xFF101414),
    onSurface = Color(0xFFE0E3E3),
    surfaceVariant = Color(0xFF3F4948),
    onSurfaceVariant = Color(0xFFBEC9C8),
    surfaceTint = Color(0xFF80D5D4),
    inverseSurface = Color(0xFFE0E3E3),
    inverseOnSurface = Color(0xFF2D3131),
    outline = Color(0xFF889392),
    outlineVariant = Color(0xFF3F4948),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF363A3A),
    surfaceDim = Color(0xFF101414),
    surfaceContainerLowest = Color(0xFF0B0F0F),
    surfaceContainerLow = Color(0xFF181C1C),
    surfaceContainer = Color(0xFF1C2020),
    surfaceContainerHigh = Color(0xFF272B2B),
    surfaceContainerHighest = Color(0xFF323535),
)

/** App-specific status colours that Material 3 has no role for. */
data class StatusColors(
    val favourite: Color,
    val favouriteContainer: Color,
    val visited: Color,
    val visitedContainer: Color,
    val onVisitedContainer: Color,
)

val LightStatusColors = StatusColors(
    favourite = Color(0xFFE09B00),
    favouriteContainer = Color(0xFFFFF0C7),
    visited = Color(0xFF2E7D4F),
    visitedContainer = Color(0xFFD7F0DF),
    onVisitedContainer = Color(0xFF0F3D22),
)

val DarkStatusColors = StatusColors(
    favourite = Color(0xFFFFC94D),
    favouriteContainer = Color(0xFF4A3A10),
    visited = Color(0xFF8AD7A6),
    visitedContainer = Color(0xFF1C4A30),
    onVisitedContainer = Color(0xFFC6F0D3),
)

/** Ward voter list brand colours (deep-blue header, stat accents, chips, status pills, bottom bar). */
data class BrandColors(
    val header: Color,
    val headerEnd: Color,
    val onHeader: Color,
    val onHeaderVariant: Color,
    val page: Color,
    val selected: Color,
    val onSelected: Color,
    val total: Color,
    val totalContainer: Color,
    val unvisited: Color,
    val unvisitedContainer: Color,
    val notVisitedPill: Color,
    val onNotVisitedPill: Color,
    val navIndicator: Color,
    val navSelected: Color,
)

val LightBrandColors = BrandColors(
    header = Color(0xFF0D3B8C),
    headerEnd = Color(0xFF1A56B8),
    onHeader = Color(0xFFFFFFFF),
    onHeaderVariant = Color(0xFFD5E1FA),
    page = Color(0xFFF5F7FB),
    selected = Color(0xFF0D3B8C),
    onSelected = Color(0xFFFFFFFF),
    total = Color(0xFF1F5FD1),
    totalContainer = Color(0xFFE4ECFD),
    unvisited = Color(0xFF55667F),
    unvisitedContainer = Color(0xFFE7ECF3),
    notVisitedPill = Color(0xFFEFF1F4),
    onNotVisitedPill = Color(0xFF454D5A),
    navIndicator = Color(0xFFDDE7FB),
    navSelected = Color(0xFF0D3B8C),
)

val DarkBrandColors = BrandColors(
    header = Color(0xFF0E2550),
    headerEnd = Color(0xFF173A78),
    onHeader = Color(0xFFFFFFFF),
    onHeaderVariant = Color(0xFFB8C7E8),
    page = Color(0xFF101414),
    selected = Color(0xFF3A66C4),
    onSelected = Color(0xFFFFFFFF),
    total = Color(0xFF9DB9FF),
    totalContainer = Color(0xFF1E2B47),
    unvisited = Color(0xFFB4C0D4),
    unvisitedContainer = Color(0xFF29303B),
    notVisitedPill = Color(0xFF2A2F36),
    onNotVisitedPill = Color(0xFFCDD2DA),
    navIndicator = Color(0xFF1F2E4D),
    navSelected = Color(0xFFB7CAFF),
)
