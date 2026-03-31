@file:OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.dashboard

import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.ui.component.LocalNavigationDispatcher
import com.neoutils.finsight.ui.component.NavigationDestination
import com.neoutils.finsight.util.LocalDateFormats
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DashboardScreen(
    openTransactions: (filterType: Transaction.Type?, filterTarget: Transaction.Target?) -> Unit = { _, _ -> },
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigationDispatcher = LocalNavigationDispatcher.current

    val transition = updateTransition(targetState = uiState)

    Scaffold(
        topBar = {
            transition.Crossfade(contentKey = { it::class }) { state ->
                when (state) {
                    is DashboardUiState.Editing -> {
                        DashboardEditToolbar(
                            onCancel = { viewModel.onAction(DashboardAction.CancelEdit) },
                            onConfirm = { viewModel.onAction(DashboardAction.ConfirmEdit) },
                        )
                    }

                    else -> {
                        TopAppBar(
                            title = {
                                Text(text = LocalDateFormats.current.yearMonth.format(uiState.yearMonth))
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = colorScheme.background,
                            ),
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(),
    ) { paddingValues ->
        transition.Crossfade(
            contentKey = { it::class },
            modifier = Modifier.padding(paddingValues),
        ) { state ->
            when (state) {
                is DashboardUiState.Loading -> DashboardLoadingContent()
                is DashboardUiState.Empty -> DashboardEmptyContent(onAction = viewModel::onAction)
                is DashboardUiState.Viewing -> DashboardViewingContent(
                    state = state,
                    openTransactions = openTransactions,
                    onOpenQuickAction = { type ->
                        when (type) {
                            QuickActionType.BUDGETS -> navigationDispatcher.dispatch(NavigationDestination.Budgets)
                            QuickActionType.CATEGORIES -> navigationDispatcher.dispatch(NavigationDestination.Categories)
                            QuickActionType.CREDIT_CARDS -> navigationDispatcher.dispatch(NavigationDestination.CreditCards())
                            QuickActionType.ACCOUNTS -> navigationDispatcher.dispatch(NavigationDestination.Accounts())
                            QuickActionType.RECURRING -> navigationDispatcher.dispatch(NavigationDestination.Recurring)
                            QuickActionType.REPORTS -> navigationDispatcher.dispatch(NavigationDestination.ReportConfig)
                            QuickActionType.INSTALLMENTS -> navigationDispatcher.dispatch(NavigationDestination.Installments)
                            QuickActionType.SUPPORT -> navigationDispatcher.dispatch(NavigationDestination.Support)
                        }
                    },

                    onAction = viewModel::onAction,
                )

                is DashboardUiState.Editing -> DashboardEditingContent(
                    state = state,
                    onAction = viewModel::onAction,
                )
            }
        }
    }
}

@Composable
private fun DashboardLoadingContent() {
    Box(modifier = Modifier.fillMaxSize())
}
