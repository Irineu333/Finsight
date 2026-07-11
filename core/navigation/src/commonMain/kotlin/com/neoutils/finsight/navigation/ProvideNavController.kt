package com.neoutils.finsight.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController

@Composable
fun ProvideNavController(
    navController: NavHostController = rememberNavController(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalNavController provides navController,
        content = content
    )
}
