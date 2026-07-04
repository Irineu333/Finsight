package com.neoutils.finsight.ui.screen.root

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.neoutils.finsight.ui.component.*
import com.neoutils.finsight.ui.screen.home.AppRoute
import com.neoutils.finsight.ui.screen.home.HomeScreen
import com.neoutils.finsight.ui.navigation.accountsGraph
import com.neoutils.finsight.ui.navigation.budgetsGraph
import com.neoutils.finsight.ui.navigation.creditCardsGraph
import com.neoutils.finsight.ui.navigation.categoriesGraph
import com.neoutils.finsight.ui.navigation.reportGraph
import com.neoutils.finsight.ui.navigation.recurringGraph
import com.neoutils.finsight.ui.navigation.supportGraph

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = AppRoute.Home,
    ) {
        composable<AppRoute.Home> {
            AnimatedVisibilityScopeProvider {
                HomeScreen()
            }
        }

        categoriesGraph(navController)

        creditCardsGraph(navController)

        accountsGraph(navController)

        budgetsGraph(navController)

        recurringGraph(navController)

        supportGraph(navController)

        reportGraph(navController)
    }
}
