package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.neoutils.finsight.feature.recurring.api.RecurringRoute
import com.neoutils.finsight.ui.screen.recurring.RecurringScreen

fun NavGraphBuilder.recurringGraph(navController: NavController) {
    composable<RecurringRoute> {
        RecurringScreen(
            onNavigateBack = { navController.navigateUp() },
        )
    }
}
