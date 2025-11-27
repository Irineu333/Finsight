package com.neoutils.finance

import androidx.compose.runtime.Composable
import com.neoutils.finance.screen.home.HomeScreen
import com.neoutils.finance.ui.theme.FinanceTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    FinanceTheme {
        HomeScreen()
    }
}