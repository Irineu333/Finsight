package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.neoutils.finsight.feature.budgets.api.BudgetsRoute
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.ui.screen.budgets.BudgetsScreen

fun NavGraphBuilder.budgetsGraph() {
    composable<BudgetsRoute> {
        val navController = LocalNavController.current

        BudgetsScreen(
            onNavigateBack = { navController.navigateUp() },
        )
    }
}
