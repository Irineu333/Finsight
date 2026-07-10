package com.neoutils.finsight.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.neoutils.finsight.feature.support.api.SupportGraph
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.ui.component.AnimatedVisibilityScopeProvider
import com.neoutils.finsight.ui.screen.support.SupportIssueRoute
import com.neoutils.finsight.ui.screen.support.SupportIssueScreen
import com.neoutils.finsight.ui.screen.support.SupportListRoute
import com.neoutils.finsight.ui.screen.support.SupportScreen

fun NavGraphBuilder.supportGraph() {
    navigation<SupportGraph>(
        startDestination = SupportListRoute,
    ) {
        composable<SupportListRoute> {
            val navController = LocalNavController.current

            AnimatedVisibilityScopeProvider {
                SupportScreen(
                    onNavigateBack = { navController.navigateUp() },
                    onOpenIssue = { issueId -> navController.navigate(SupportIssueRoute(issueId)) },
                )
            }
        }

        composable<SupportIssueRoute> { backStackEntry ->
            val navController = LocalNavController.current
            val route = backStackEntry.toRoute<SupportIssueRoute>()

            AnimatedVisibilityScopeProvider {
                SupportIssueScreen(
                    issueId = route.issueId,
                    onNavigateBack = { navController.navigateUp() },
                )
            }
        }
    }
}
