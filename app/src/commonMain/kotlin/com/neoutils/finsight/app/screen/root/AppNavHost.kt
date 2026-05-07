package com.neoutils.finsight.app.screen.root

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.neoutils.finsight.app.route.AppRoute
import com.neoutils.finsight.core.ui.component.*
import com.neoutils.finsight.feature.home.dispatcher.NavigationDestination
import com.neoutils.finsight.feature.home.dispatcher.NavigationDispatcherProvider
import com.neoutils.finsight.feature.home.dispatcher.rememberNavigationDispatcher
import com.neoutils.finsight.feature.accounts.screen.AccountsScreen
import com.neoutils.finsight.feature.budgets.screen.BudgetsScreen
import com.neoutils.finsight.feature.categories.modal.viewCategory.ViewCategoryModal
import com.neoutils.finsight.feature.categories.screen.CategoriesScreen
import com.neoutils.finsight.feature.creditCards.screen.CreditCardsScreen
import com.neoutils.finsight.feature.home.route.HomeScreen
import com.neoutils.finsight.feature.installments.screen.InstallmentsScreen
import com.neoutils.finsight.feature.transactions.modal.addTransaction.AddTransactionModal
import com.neoutils.finsight.feature.creditCards.screen.invoiceTransactions.InvoiceTransactionsScreen
import com.neoutils.finsight.feature.recurring.screen.RecurringScreen
import com.neoutils.finsight.feature.report.model.PerspectiveTab
import com.neoutils.finsight.feature.report.model.toRoute
import com.neoutils.finsight.feature.report.route.PerspectiveTabNavType
import com.neoutils.finsight.feature.report.route.ReportRoute
import com.neoutils.finsight.feature.report.screen.config.ReportConfigScreen
import com.neoutils.finsight.feature.report.screen.viewer.ReportViewerScreen
import com.neoutils.finsight.feature.support.screen.SupportIssueScreen
import com.neoutils.finsight.feature.support.screen.SupportScreen
import kotlin.reflect.typeOf

@Composable
fun AppNavHost() = Surface {

    val navController = rememberNavController()
    val navigationDispatcher = rememberNavigationDispatcher()

    LaunchedEffect(navigationDispatcher, navController) {
        navigationDispatcher.events.collect { destination ->
            when (destination) {
                NavigationDestination.Categories -> {
                    navController.navigate(AppRoute.Categories)
                }

                is NavigationDestination.InvoiceTransactions -> {
                    navController.navigate(AppRoute.InvoiceTransactions(destination.creditCardId))
                }

                is NavigationDestination.CreditCards -> {
                    navController.navigate(AppRoute.CreditCards(destination.creditCardId))
                }

                is NavigationDestination.Accounts -> {
                    navController.navigate(AppRoute.Accounts(destination.accountId))
                }

                NavigationDestination.Installments -> {
                    navController.navigate(AppRoute.Installments)
                }

                NavigationDestination.Budgets -> {
                    navController.navigate(AppRoute.Budgets)
                }

                NavigationDestination.Recurring -> {
                    navController.navigate(AppRoute.Recurring)
                }

                NavigationDestination.ReportConfig -> {
                    navController.navigate(AppRoute.Reports)
                }

                NavigationDestination.Support -> {
                    navController.navigate(AppRoute.Support)
                }
            }
        }
    }

    FormattingLocalsHost {
        NavigationDispatcherProvider(navigationDispatcher) {
            ModalManagerHost {
                SharedTransitionProvider {
                    NavHost(
                        navController = navController,
                        startDestination = AppRoute.Home,
                    ) {
                        composable<AppRoute.Home> {
                            val modalManager = LocalModalManager.current
                            AnimatedVisibilityScopeProvider {
                                HomeScreen(
                                    onAddTransaction = {
                                        modalManager.show(AddTransactionModal())
                                    },
                                )
                            }
                        }

                        composable<AppRoute.Categories> {
                            val modalManager = LocalModalManager.current
                            CategoriesScreen(
                                onNavigateBack = {
                                    navController.navigateUp()
                                },
                                onCategoryClick = { category ->
                                    modalManager.show(ViewCategoryModal(categoryId = category.id))
                                }
                            )
                        }

                        composable<AppRoute.CreditCards> { backStackEntry ->
                            val route = backStackEntry.toRoute<AppRoute.CreditCards>()
                            AnimatedVisibilityScopeProvider {
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

                        composable<AppRoute.Support> {
                            AnimatedVisibilityScopeProvider {
                                SupportScreen(
                                    onNavigateBack = {
                                        navController.navigateUp()
                                    },
                                    onOpenIssue = { issueId ->
                                        navController.navigate(AppRoute.SupportIssue(issueId))
                                    },
                                )
                            }
                        }

                        composable<AppRoute.SupportIssue> { backStackEntry ->
                            AnimatedVisibilityScopeProvider {
                                val route = backStackEntry.toRoute<AppRoute.SupportIssue>()
                                SupportIssueScreen(
                                    issueId = route.issueId,
                                    onNavigateBack = {
                                        navController.navigateUp()
                                    },
                                )
                            }
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
