package com.neoutils.finsight.ui.screen.root

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
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
fun AppNavHost() = Surface {

    val navController = rememberNavController()
    val navigationDispatcher = rememberAppNavigationDispatcher(navController)

    FormattingLocalsHost {
        NavigationDispatcherProvider(dispatcher = navigationDispatcher) {
            ModalManagerHost {
                SharedTransitionProvider {
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
            }
        }
    }
}
