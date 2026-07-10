package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.neoutils.finsight.feature.accounts.api.AccountsRoute
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.ui.screen.accounts.AccountsScreen
import kotlinx.serialization.Serializable

@Serializable
data object AccountsGraph

fun NavGraphBuilder.accountsGraph() {
    navigation<AccountsGraph>(
        startDestination = AccountsRoute(),
    ) {
        composable<AccountsRoute> { backStackEntry ->
            val navController = LocalNavController.current
            val route = backStackEntry.toRoute<AccountsRoute>()

            AccountsScreen(
                initialAccountId = route.accountId,
                onNavigateBack = { navController.navigateUp() },
            )
        }
    }
}
