package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.neoutils.finsight.feature.budgets.api.BudgetsRoute
import com.neoutils.finsight.ui.screen.budgets.BudgetsScreen

fun NavGraphBuilder.budgetsGraph(navController: NavController) {
    composable<BudgetsRoute> {
        BudgetsScreen(
            onNavigateBack = { navController.navigateUp() },
        )
    }
}
