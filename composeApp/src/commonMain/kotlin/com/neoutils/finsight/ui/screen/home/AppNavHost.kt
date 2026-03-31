package com.neoutils.finsight.ui.screen.home

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.neoutils.finsight.ui.component.*
import com.neoutils.finsight.ui.screen.accounts.AccountsScreen
import com.neoutils.finsight.ui.screen.budgets.BudgetsScreen
import com.neoutils.finsight.ui.screen.categories.CategoriesScreen
import com.neoutils.finsight.ui.screen.creditCards.CreditCardsScreen
import com.neoutils.finsight.ui.screen.installments.InstallmentsScreen
import com.neoutils.finsight.ui.screen.invoiceTransactions.InvoiceTransactionsScreen
import com.neoutils.finsight.ui.screen.recurring.RecurringScreen
import com.neoutils.finsight.ui.screen.report.ReportRoute
import com.neoutils.finsight.ui.screen.report.toParams
import com.neoutils.finsight.ui.screen.report.toRoute
import com.neoutils.finsight.ui.screen.report.config.PerspectiveTab
import com.neoutils.finsight.ui.screen.report.config.ReportConfigScreen
import com.neoutils.finsight.ui.screen.report.viewer.ReportViewerScreen
import com.neoutils.finsight.util.PerspectiveTabNavType
import kotlin.reflect.typeOf

@Composable
fun AppNavHost() = Surface {

    val navController = rememberNavController()
    val navigationDispatcher = rememberAppNavigationDispatcher(navController)

    FormattingLocalsHost {
        ModalManagerHost {
            NavigationDispatcherProvider(dispatcher = navigationDispatcher) {
                SharedTransitionProvider {
                    NavHost(
                        navController = navController,
                        startDestination = AppRoute.Home,
                    ) {
                        composable<AppRoute.Home> {
                            CompositionLocalProvider(
                                LocalAnimatedVisibilityScope provides this
                            ) {
                                HomeScreen()
                            }
                        }

                        composable<AppRoute.Categories> {
                            CategoriesScreen(
                                onNavigateBack = {
                                    navController.navigateUp()
                                }
                            )
                        }

                        composable<AppRoute.CreditCards> { backStackEntry ->
                            val route = backStackEntry.toRoute<AppRoute.CreditCards>()
                            CompositionLocalProvider(
                                LocalAnimatedVisibilityScope provides this
                            ) {
                                CreditCardsScreen(
                                    initialCreditCardId = route.creditCardId,
                                    onNavigateBack = {
                                        navController.navigateUp()
                                    }
                                )
                            }
                        }

                        composable<AppRoute.InvoiceTransactions> { backStackEntry ->
                            val route = backStackEntry.toRoute<AppRoute.InvoiceTransactions>()
                            InvoiceTransactionsScreen(
                                creditCardId = route.creditCardId,
                                onNavigateBack = {
                                    navController.navigateUp()
                                }
                            )
                        }

                        composable<AppRoute.Accounts> { backStackEntry ->
                            val route = backStackEntry.toRoute<AppRoute.Accounts>()
                            AccountsScreen(
                                initialAccountId = route.accountId,
                                onNavigateBack = {
                                    navController.navigateUp()
                                }
                            )
                        }

                        composable<AppRoute.Installments> {
                            InstallmentsScreen(
                                onNavigateBack = {
                                    navController.navigateUp()
                                }
                            )
                        }

                        composable<AppRoute.Budgets> {
                            BudgetsScreen(
                                onNavigateBack = {
                                    navController.navigateUp()
                                }
                            )
                        }

                        composable<AppRoute.Recurring> {
                            RecurringScreen(
                                onNavigateBack = {
                                    navController.navigateUp()
                                }
                            )
                        }

                        navigation<AppRoute.Reports>(
                            startDestination = ReportRoute.Config,
                        ) {
                            composable<ReportRoute.Config> {
                                ReportConfigScreen(
                                    onNavigateBack = {
                                        navController.navigateUp()
                                    },
                                    onNavigateToViewer = { params ->
                                        navController.navigate(params.toRoute())
                                    },
                                )
                            }

                            composable<ReportRoute.Viewer>(
                                typeMap = mapOf(
                                    typeOf<PerspectiveTab>() to PerspectiveTabNavType()
                                )
                            ) { backStackEntry ->
                                val route = backStackEntry.toRoute<ReportRoute.Viewer>()
                                ReportViewerScreen(
                                    route = route,
                                    onNavigateBack = {
                                        navController.navigateUp()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
