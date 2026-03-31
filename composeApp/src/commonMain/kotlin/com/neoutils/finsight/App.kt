package com.neoutils.finsight

import androidx.compose.runtime.Composable
import com.neoutils.finsight.ui.screen.home.AppNavHost
import com.neoutils.finsight.ui.theme.FinsightTheme

@Composable
fun App() {
    FinsightTheme {
        AppNavHost()
    }
}
