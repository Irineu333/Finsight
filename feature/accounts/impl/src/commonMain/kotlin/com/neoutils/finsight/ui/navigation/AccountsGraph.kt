package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.neoutils.finsight.feature.accounts.api.AccountsRoute
import com.neoutils.finsight.ui.screen.accounts.AccountsScreen

fun NavGraphBuilder.accountsGraph(navController: NavController) {
    composable<AccountsRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<AccountsRoute>()
        AccountsScreen(
            initialAccountId = route.accountId,
            onNavigateBack = { navController.navigateUp() },
        )
    }
}
