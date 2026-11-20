package com.example.etikoppakavoterdirectory.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val Base = Typography()

/**
 * Slightly larger, sturdier type for outdoor use. Sizes are in sp, so the user's system font
 * scale is always respected.
 */
val Typography = Typography(
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = Base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.2.sp),
    bodyMedium = Base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.15.sp),
    bodySmall = Base.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = Base.labelMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = Base.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
)

/**
 * Telugu script needs more vertical room than Latin text: vowel signs and conjuncts extend above
 * and below the line and get clipped at Latin line heights. The system Noto Sans Telugu font is used.
 */
fun TextStyle.telugu(): TextStyle = copy(lineHeight = 1.55.em, letterSpacing = 0.sp)
