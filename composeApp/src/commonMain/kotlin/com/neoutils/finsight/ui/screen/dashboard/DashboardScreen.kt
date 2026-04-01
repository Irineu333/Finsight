@file:OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.dashboard

import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.dashboard_edit_cancel
import com.neoutils.finsight.resources.dashboard_edit_confirm
import com.neoutils.finsight.resources.dashboard_edit_title
import com.neoutils.finsight.ui.component.LocalNavigationDispatcher
import com.neoutils.finsight.ui.component.NavigationDestination
import com.neoutils.finsight.ui.screen.home.HomeChromeConfig
import com.neoutils.finsight.ui.screen.home.HomeChromeEffect
import com.neoutils.finsight.util.LocalDateFormats
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DashboardScreen(
    openTransactions: (filterType: Transaction.Type?, filterTarget: Transaction.Target?) -> Unit = { _, _ -> },
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigationDispatcher = LocalNavigationDispatcher.current

    HomeChromeEffect(
        config = when (uiState) {
            is DashboardUiState.Loading,
            is DashboardUiState.Empty,
            is DashboardUiState.Viewing -> {
                HomeChromeConfig.Default
            }

            is DashboardUiState.Editing -> {
                HomeChromeConfig.ContentOnly
            }
        }
    )

    val transition = updateTransition(targetState = uiState)

    Scaffold(
        topBar = {
            transition.Crossfade(
                contentKey = { it::class }
            ) { state ->
                when (state) {
                    is DashboardUiState.Editing -> {
                        CenterAlignedTopAppBar(
                            navigationIcon = {
                                TextButton(
                                    onClick = {
                                        viewModel.onAction(DashboardAction.CancelEdit)
                                    }
                                ) {
                                    Text(text = stringResource(Res.string.dashboard_edit_cancel))
                                }
                            },
                            title = {
                                Text(
                                    text = stringResource(Res.string.dashboard_edit_title),
                                )
                            },
                            actions = {
                                TextButton(
                                    onClick = {
                                        viewModel.onAction(DashboardAction.ConfirmEdit)
                                    }
                                ) {
                                    Text(text = stringResource(Res.string.dashboard_edit_confirm))
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = colorScheme.background,
                            ),
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
                is DashboardUiState.Loading -> Unit
                is DashboardUiState.Empty -> {
                    DashboardEmptyContent(
                        onAction = viewModel::onAction
                    )
                }

                is DashboardUiState.Viewing -> {
                    DashboardViewingContent(
                        state = state,
                        openTransactions = openTransactions,
                        onAction = viewModel::onAction,
                    )
                }

                is DashboardUiState.Editing -> {
                    DashboardEditingContent(
                        state = state,
                        onAction = viewModel::onAction,
                    )
                }
            }
        }
    }
}
