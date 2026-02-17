package com.neoutils.finance

import androidx.compose.runtime.Composable
import com.neoutils.finance.ui.screen.home.AppNavHost
import com.neoutils.finance.ui.theme.FinanceTheme

@Composable
fun App() {
    FinanceTheme {
        AppNavHost()
    }
}