package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.neoutils.finsight.feature.accounts.api.AccountsRoute
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.navigation.NavGraphRoute
import com.neoutils.finsight.navigation.NavRoute
import com.neoutils.finsight.ui.screen.accounts.AccountsScreen
import com.neoutils.finsight.ui.screen.archived.ArchivedAccountsScreen
import kotlinx.serialization.Serializable

@Serializable
data object AccountsGraph : NavGraphRoute

// Internal destination: only reached from within the accounts feature (design D6),
// so it lives in the impl rather than the api.
@Serializable
data object ArchivedAccountsRoute : NavRoute

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

        composable<ArchivedAccountsRoute> {
            val navController = LocalNavController.current

            ArchivedAccountsScreen(
                onNavigateBack = { navController.navigateUp() },
            )
        }
    }
}
