package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.neoutils.finsight.feature.budgets.api.BudgetsRoute
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.navigation.NavGraphRoute
import com.neoutils.finsight.ui.screen.budgets.BudgetsScreen
import kotlinx.serialization.Serializable

@Serializable
data object BudgetsGraph : NavGraphRoute

fun NavGraphBuilder.budgetsGraph() {
    navigation<BudgetsGraph>(
        startDestination = BudgetsRoute,
    ) {
        composable<BudgetsRoute> {
            val navController = LocalNavController.current

            BudgetsScreen(
                onNavigateBack = { navController.navigateUp() },
            )
        }
    }
}
