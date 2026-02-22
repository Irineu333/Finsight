package com.neoutils.finsight.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content,
    )
}
