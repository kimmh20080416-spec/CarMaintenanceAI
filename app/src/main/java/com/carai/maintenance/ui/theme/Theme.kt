package com.carai.maintenance.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val CarBlack = Color(0xFF111111)
val CarWhite = Color(0xFFFFFFFF)
val CarGray = Color(0xFF757575)
val CarLightGray = Color(0xFFF2F2F2)
val CarAccent = Color(0xFF2B2B2B)

private val LightColors = lightColorScheme(
    primary = CarBlack,
    onPrimary = CarWhite,
    secondary = CarGray,
    onSecondary = CarWhite,
    background = CarWhite,
    onBackground = CarBlack,
    surface = CarWhite,
    onSurface = CarBlack,
    surfaceVariant = CarLightGray,
    onSurfaceVariant = CarBlack,
    outline = CarGray
)

val CarTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, color = CarBlack),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = CarBlack),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, color = CarBlack),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, color = CarBlack),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, color = CarGray),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, color = CarWhite)
)

@Composable
fun CarMaintenanceAITheme(content: @Composable () -> Unit) {
    // 항상 라이트(흰 배경/검정 텍스트) 모던 테마 고정
    MaterialTheme(
        colorScheme = LightColors,
        typography = CarTypography,
        content = content
    )
}
