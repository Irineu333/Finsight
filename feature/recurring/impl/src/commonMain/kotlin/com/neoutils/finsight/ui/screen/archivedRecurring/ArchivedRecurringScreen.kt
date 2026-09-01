@file:OptIn(ExperimentalMaterial3Api::class)

package com.neoutils.finsight.ui.screen.archivedRecurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.feature.shell.api.ChromeConfig
import com.neoutils.finsight.feature.shell.api.ChromeEffect
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.recurring_archived_empty
import com.neoutils.finsight.resources.recurring_archived_title
import com.neoutils.finsight.ui.component.LocalDetailPaneController
import com.neoutils.finsight.ui.modal.viewRecurring.ViewRecurringModal
import com.neoutils.finsight.ui.screen.recurring.RecurringCard
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Where an archived recurring stays reachable after it leaves the monthly list.
 *
 * The destination is not a convenience: archiving is offered to the user as reversible,
 * un-archiving is reached from the detail sheet, and the detail sheet is reached from a
 * list — without one, an archived recurring would have no way back.
 *
 * **No month, no summary, no section.** An archived template generates no cycle, so it
 * has no state of cycle to be grouped by, no month to be cut by and no figure to be
 * summed into. And no row says *archived* either: here every one of them is, so the mark
 * would tell no row from its neighbour — which is the test the row applies to everything
 * it asserts.
 */
@Composable
fun ArchivedRecurringScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: ArchivedRecurringViewModel = koinViewModel(),
) {
    val analytics = koinInject<Analytics>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val detailController = LocalDetailPaneController.current

    LaunchedEffect(Unit) {
        analytics.logScreenView("archived_recurring")
    }

    // What is archived is looked at and brought back, never added to.
    ChromeEffect(config = ChromeConfig.NoButtonOverContent)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(Res.string.recurring_archived_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.background,
                    titleContentColor = colorScheme.onBackground,
                    navigationIconContentColor = colorScheme.onBackground,
                ),
            )
        },
        modifier = Modifier.testTag("screen_archived_recurring"),
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { paddingValues ->
        when (val state = uiState) {
            ArchivedRecurringUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            ArchivedRecurringUiState.Empty -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.recurring_archived_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                )
            }

            is ArchivedRecurringUiState.Content -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = state.recurring,
                    key = { "archived_recurring_${it.recurring.id}" },
                ) { item ->
                    // The same row the monthly list draws: this is the same kind of
                    // thing, seen where it went, and a second component for it would be
                    // a second vocabulary for one object.
                    RecurringCard(
                        row = item.row,
                        onClick = {
                            detailController.show(ViewRecurringModal(item.recurring.id))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
