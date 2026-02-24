package com.neoutils.finsight.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Primary1,
    onPrimary = Color.White,
    primaryContainer = Primary1,
    onPrimaryContainer = Color.White,

    secondary = Income,
    onSecondary = Color.White,
    secondaryContainer = IncomeCardBackgroundLight,
    onSecondaryContainer = LightTextPrimary,

    tertiary = Expense,
    onTertiary = Color.White,
    tertiaryContainer = ExpenseCardBackgroundLight,
    onTertiaryContainer = LightTextPrimary,

    background = LightSurface2,
    onBackground = LightTextPrimary,

    surface = LightCardBackground,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurface2,
    onSurfaceVariant = LightTextSecondary,

    surfaceContainer = LightCardBackground,
    surfaceContainerLow = LightCardBackground,
    surfaceContainerHighest = LightSurface2,

    error = Error,
    onError = Color.White,

    outline = LightDivider,
    outlineVariant = LightDividerVariant,
)

private val DarkColorScheme = darkColorScheme(
    primary = Primary1,
    onPrimary = Color.White,
    primaryContainer = Primary1,
    onPrimaryContainer = Color.White,

    secondary = Income,
    onSecondary = Color.White,
    secondaryContainer = IncomeCardBackground,
    onSecondaryContainer = Color.White,

    tertiary = Expense,
    onTertiary = Color.White,
    tertiaryContainer = ExpenseCardBackground,
    onTertiaryContainer = Color.White,

    background = Surface1,
    onBackground = Color.White,

    surface = Surface2,
    onSurface = Color.White,
    surfaceVariant = Surface3,
    onSurfaceVariant = TextLight1,

    surfaceContainer = Surface2,
    surfaceContainerLow = Surface2,
    surfaceContainerHighest = Surface3,

    error = Error,
    onError = Color.White,

    outline = DividerColor,
    outlineVariant = TextLight2,
)

@Composable
fun FinsightTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = AppTypography,
        content = content,
    )
}
