package com.neoutils.finance

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.neoutils.finance.home.HomeScreen
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    MaterialTheme {
        HomeScreen()
    }
}